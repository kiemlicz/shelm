package com.shelm

import java.io.FileReader

import com.shelm.ChartPackagingSettings.{ChartYaml, ValuesYaml}
import io.circe.syntax._
import io.circe.{yaml, Json}
import sbt.Keys._
import sbt._

import scala.annotation.tailrec
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

object HelmPlugin extends AutoPlugin {
  override def trigger = noTrigger

  object autoImport {
    val Helm: Configuration = config("helm")
    // format: off
    lazy val chartSettings = settingKey[Seq[ChartPackagingSettings]]("All per-Chart settings")
    lazy val chartUri = settingKey[ChartLocation]("Chart URI, can be stable/chartName, file:/path/to/tgz.tgz")

    lazy val prepare = taskKey[Seq[File]]("Download Chart if remote, copy all includes into Chart directory, return Chart directory")
    lazy val lint = taskKey[Seq[File]]("Lint Helm Chart")
    lazy val packagesBin = taskKey[Seq[File]]("Create Helm Charts")
    // format: on

    lazy val baseHelmSettings: Seq[Setting[_]] = Seq(
      chartSettings := Seq.empty[ChartPackagingSettings],
      prepare := {
        chartSettings.value.map {
          settings =>
            val tempChartDir = ChartDownloader.download(settings.chartLocation, target.value)
            val chartYaml = readChart(tempChartDir / ChartYaml)
            val updatedChartYaml = settings.chartUpdate(chartYaml)
            settings.includeFiles.foreach {
              case (src, d) =>
                val dst = tempChartDir / d
                if (src.isDirectory) IO.copyDirectory(src, dst, overwrite = true)
                else IO.copyFile(src, dst)
            }
            settings.yamlsToMerge.foreach {
              case (overrides, onto) =>
                val dst = tempChartDir / onto
                if (dst.exists())
                  IO.write(
                    dst,
                    resultOrThrow(for {
                      overrides <- yaml.parser.parse(new FileReader(overrides))
                      onto <- yaml.parser.parse(new FileReader(dst))
                    } yield yaml.printer.print(onto.deepMerge(overrides))),
                  )
                else IO.copyFile(overrides, dst)
            }
            val valuesFile = tempChartDir / ValuesYaml
            val valuesJson = if (valuesFile.exists()) yaml.parser.parse(new FileReader(valuesFile)).toOption else None
            val overrides = mergeOverrides(settings.valueOverrides(valuesJson))
            overrides.foreach { valuesOverride =>
              IO.write(
                valuesFile,
                if (valuesFile.exists())
                  resultOrThrow(
                    yaml.parser
                      .parse(new FileReader(valuesFile))
                      .map(onto => yaml.printer.print(onto.deepMerge(valuesOverride)))
                  )
                else yaml.printer.print(valuesOverride),
              )
            }
            IO.write(tempChartDir / ChartYaml, yaml.printer.print(updatedChartYaml.asJson))
            cleanFiles ++= Seq(tempChartDir)
            tempChartDir
        }
      },
      lint := {
        val log = streams.value.log
        prepare.value.zip(chartSettings.value).map {
          case (chartDir, settings) =>
            lintChart(chartDir, settings.fatalLint, log)
        }
      },
      packagesBin := {
        lint.value.zip(chartSettings.value).map {
          case (linted, settings) =>
            val chartYaml = readChart(linted / ChartYaml)
            buildChart(
              linted,
              chartYaml.name,
              chartYaml.version,
              settings.destination,
              settings.dependencyUpdate,
              streams.value.log,
            )
        }
      },
    )
  }

  import autoImport._

  private[this] def lintChart(chartDir: File, fatalLint: Boolean, log: Logger): File = {
    log.info("Linting Helm Package")
    val cmd = s"helm lint $chartDir"
    try {
      startProcess(cmd, log)
    } catch {
      case NonFatal(e) if fatalLint => throw e
      case NonFatal(e) => log.error(s"Helm lint has failed ${e.getMessage}, continuing")
    }
    chartDir
  }

  private[this] def buildChart(
    chartDir: File,
    chartName: String,
    chartVersion: String,
    targetDir: File,
    dependencyUpdate: Boolean,
    log: Logger,
  ): File = {
    val opts = s"${if (dependencyUpdate) " -u" else ""}"
    val dest = s" -d $targetDir"
    val cmd = s"helm package$opts$dest $chartDir"
    val output = targetDir / s"$chartName-$chartVersion.tgz"
    log.info(s"Creating Helm Package: $cmd")

    //https://github.com/helm/helm/issues/2258
    @tailrec def go(n: Int, result: Try[Unit]): File = result match {
      case Success(_) => output
      case f @ Failure(e) if n > 0 =>
        log.warn(s"Couldn't performs: $cmd, failed with: ${e.getMessage}, retrying")
        go(n - 1, f)
      case Failure(exception) =>
        log.err(s"Couldn't performs: $cmd, retries limit reached")
        throw exception
    }
    go(3, Try(startProcess(cmd, log)))
  }

  private[shelm] def startProcess(cmd: String, log: Logger): Unit =
    sys.process.Process(command = cmd) ! log match {
      case 0 => ()
      case exitCode => sys.error(s"The command: $cmd, failed with: $exitCode")
    }

  private[shelm] def startProcess(cmd: String): Unit =
    sys.process.Process(command = cmd) ! match {
      case 0 => ()
      case exitCode => sys.error(s"The command: $cmd, failed with: $exitCode")
    }

  private[this] def readChart(file: File) = resultOrThrow(
    yaml.parser.parse(new FileReader(file)).flatMap(_.as[Chart])
  )

  private[this] def mergeOverrides(overrides: Seq[Json]): Option[Json] = {
    val merged = overrides.foldLeft(Json.Null)(_.deepMerge(_))
    if (overrides.isEmpty) None else Some(merged)
  }

  override lazy val projectSettings: Seq[Setting[_]] =
    inConfig(Helm)(baseHelmSettings)

  override def projectConfigurations: Seq[Configuration] = Seq(Helm)
}
