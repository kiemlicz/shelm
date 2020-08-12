package com.shelm

import java.io.FileReader

import com.shelm.ChartPackagingSettings.{ChartYaml, ValuesYaml}
import io.circe.syntax._
import io.circe.{yaml, Json}
import sbt.Keys._
import sbt._

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
        prepare.value.map(c => lintChart(c, log))
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

  private[this] def lintChart(chartDir: File, log: Logger): File = {
    log.info("Linting Helm Package")
    val cmd = s"helm lint $chartDir"
    startProcess(cmd, log, chartDir)
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
    log.info(s"Creating Helm Package: $cmd")
    startProcess(cmd, log, targetDir / s"$chartName-$chartVersion.tgz")
  }

  private[shelm] def startProcess[T](cmd: String, log: Logger, onSuccess: => T): T =
    sys.process.Process(command = cmd) ! log match {
      case 0 => onSuccess
      case exitCode => sys.error(s"The command: $cmd, failed with: $exitCode")
    }

  private[shelm] def startProcess[T](cmd: String, onSuccess: => T): T =
    sys.process.Process(command = cmd) ! match {
      case 0 => onSuccess
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
