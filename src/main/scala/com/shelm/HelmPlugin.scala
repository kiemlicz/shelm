package com.shelm

import com.shelm.ChartPackagingSettings.{ChartYaml, ValuesYaml}
import com.shelm.ChartRepositorySettings.{Cert, NoAuth, UserPassword}
import com.shelm.exception.HelmCommandException
import io.circe.syntax._
import io.circe.{yaml, Json}
import sbt.Keys._
import sbt._

import java.io.FileReader
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import scala.annotation.tailrec
import scala.concurrent.duration.{FiniteDuration, SECONDS}

object HelmPlugin extends AutoPlugin {
  override def trigger = noTrigger

  object autoImport {
    val Helm: Configuration = config("helm")
    // format: off
    lazy val repositories = settingKey[Seq[ChartRepository]]("Additional Repositories settings")
    lazy val shouldUpdateRepositories = settingKey[Boolean]("Perform `helm repo update` at the helm:prepare beginning")
    lazy val chartSettings = settingKey[Seq[ChartPackagingSettings]]("All per-Chart settings")

    lazy val addRepositories = taskKey[Seq[ChartRepository]]("Setup Helm Repositories")
    lazy val updateRepositories = taskKey[Unit]("Update Helm Repositories")
    lazy val prepare = taskKey[Seq[File]]("Download Chart if not present locally, copy all includes into Chart directory, return Chart directory")
    lazy val lint = taskKey[Seq[File]]("Lint Helm Chart")
    lazy val packagesBin = taskKey[Seq[File]]("Create Helm Charts")
    // format: on

    lazy val baseHelmSettings: Seq[Setting[_]] = Seq(
      repositories := Seq.empty,
      shouldUpdateRepositories := false,
      chartSettings := Seq.empty[ChartPackagingSettings],
      addRepositories := {
        val log = streams.value.log
        repositories.value.map { r =>
          ensureRepo(r, log)
          r
        }
      },
      updateRepositories := {
        val log = streams.value.log
        updateRepo(log)
      },
      prepare := {
        val log = streams.value.log
        val _ = Def.taskIf {
          if (shouldUpdateRepositories.value) updateRepositories.value
          else ()
        }.value
        chartSettings.value.map {
          settings =>
            val tempChartDir = ChartDownloader.download(settings.chartLocation, target.value, log)
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

  val random = new SecureRandom
  import autoImport._

  private[this] def ensureRepo(repo: ChartRepository, log: Logger): Unit = {
    log.info(s"Adding $repo to Helm Repositories")
    val options = chartRepositoryCommandFlags(repo.settings)
    val cmd = s"helm repo add ${repo.name.name} ${repo.uri} $options"
    HelmProcessResult.throwOnFailure(startProcess(cmd))
  }

  private[this] def updateRepo(log: Logger): Unit = {
    log.info("Updating Helm Repositories")
    HelmProcessResult.throwOnFailure(startProcess("helm repo update"))
  }

  private[this] def lintChart(chartDir: File, fatalLint: Boolean, log: Logger): File = {
    log.info("Linting Helm Package")
    val cmd = s"helm lint $chartDir"
    val result = startProcess(cmd)
    if (HelmProcessResult.isFailure(result) && fatalLint) {
      throw new HelmCommandException(result.output.buf.toString, result.exitCode)
    }
    chartDir
  }

  private[shelm] def pullChart(options: String, location: File, log: Logger): File = {
    val cmd = s"helm pull $options"
    IO.delete(location) // ensure dir is empty
    retrying(cmd, log)
    location
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
    retrying(cmd, log)
    output
  }

  /**
    * Retry given command
    * That method exists only due to: https://github.com/helm/helm/issues/2258
    * @param cmd shell command that will potentially be retried
    * @param n number of tries
    */
  private[this] def retrying(cmd: String, sbtLogger: Logger, n: Int = 3): Unit = {
    val initialSleep = FiniteDuration(1, SECONDS)
    val backOff = 2
    @tailrec
    def go(n: Int, result: HelmProcessResult, sleep: FiniteDuration): Unit = result match {
      case HelmProcessResult(0, logger) =>
        val output = logger.buf.toString
        if (output.nonEmpty)
          sbtLogger.info(s"Helm command ('$cmd') success, output:\n$output")
        else
          sbtLogger.info(s"Helm command ('$cmd') success")
      case HelmProcessResult(exitCode, logger) if n > 0 =>
        sbtLogger.warn(
          s"Couldn't perform: $cmd (exit code: $exitCode), failed with: ${logger.buf.toString}, retrying in: $sleep"
        )
        Thread.sleep(sleep.toMillis)
        val nextSleep = (sleep * backOff) + FiniteDuration(random.nextInt() % 1000, TimeUnit.MILLISECONDS)
        go(n - 1, startProcess(cmd), nextSleep)
      case r =>
        sbtLogger.err(s"Couldn't perform: $cmd, retries limit reached")
        HelmProcessResult.throwOnFailure(r)
    }
    go(n, startProcess(cmd), initialSleep)
  }

  private[shelm] def startProcess(cmd: String): HelmProcessResult = {
    val logger = new BufferingProcessLogger()
    val exitCode = sys.process.Process(command = cmd) ! logger
    HelmProcessResult(exitCode, logger)
  }

  private[this] def readChart(file: File) = resultOrThrow(
    yaml.parser.parse(new FileReader(file)).flatMap(_.as[Chart])
  )

  private[this] def mergeOverrides(overrides: Seq[Json]): Option[Json] = {
    val merged = overrides.foldLeft(Json.Null)(_.deepMerge(_))
    if (overrides.isEmpty) None else Some(merged)
  }

  private[shelm] def chartRepositoryCommandFlags(settings: ChartRepositorySettings): String = settings match {
    case NoAuth => ""
    case UserPassword(user, password) => s"--username $user --password $password"
    case Cert(certFile, keyFile, ca) =>
      s"--cert-file ${certFile.getAbsolutePath} --key-file ${keyFile.getAbsolutePath} ${ca.map(ca => s"--ca-file $ca").getOrElse("")}"
  }

  override lazy val projectSettings: Seq[Setting[_]] =
    inConfig(Helm)(baseHelmSettings)

  override def projectConfigurations: Seq[Configuration] = Seq(Helm)
}
