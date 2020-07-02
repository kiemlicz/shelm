package com.shelm

import java.io.FileReader

import io.circe.yaml
import sbt.Keys._
import sbt._

object HelmPlugin extends AutoPlugin {
  object autoImport {
    val Helm = config("helm")

    val helmChart = settingKey[File]("Chart directory")
    val helmChartName = settingKey[String]("Chart name")
    val helmChartVersion = settingKey[String]("Chart version")
    val helmDestination = settingKey[File]("Chart destination directory")

    val helmPackage = taskKey[File]("Create Helm package")

    private val helmChartYaml = settingKey[Chart]("Parsed Chart")

    lazy val baseHelmSettings: Seq[Setting[_]] = Seq(
      helmDestination := target.value,
      helmChartYaml := resultOrThrow(
        yaml.parser
          .parse(new FileReader(helmChart.value / ChartYaml))
          .flatMap(_.as[Chart])
      ),
      helmChartName := helmChartYaml.value.name,
      helmChartVersion := helmChartYaml.value.version,
      helmPackage := {
        val linted = lintChart(helmChart.value, streams.value.log)
        buildChart(
          linted,
          helmChartName.value,
          helmChartVersion.value,
          helmDestination.value,
          streams.value.log
        )
      },
    )
  }
  private val ChartYaml = "Chart.yaml"

  import autoImport._

  private[this] def lintChart(chartDir: File, log: Logger): File = {
    log.info("Linting Helm Package")
    val cmd = s"helm lint ."
    sys.process.Process(command = cmd, cwd = Some(chartDir)) ! log match {
      case 0        => chartDir
      case exitCode => sys.error(s"The command: $cmd, failed with: $exitCode")
    }
  }

  private[this] def buildChart(chartDir: File,
                               chartName: String,
                               chartVersion: String,
                               targetDir: File,
                               log: Logger): File = {
    log.info("Creating Helm Package")
    val cmd = s"helm package -u -d $targetDir ."
    sys.process.Process(command = cmd, cwd = Some(chartDir)) ! log match {
      case 0        => targetDir / s"$chartName-$chartVersion.tgz"
      case exitCode => sys.error(s"The command: $cmd, failed with: $exitCode")
    }
  }

  override def trigger = allRequirements

  override lazy val projectSettings: Seq[Setting[_]] =
    inConfig(Helm)(baseHelmSettings)

  private[this] def resultOrThrow[R](r: Either[Throwable, R]): R = r match {
    case Right(value) => value
    case Left(err)    => throw err
  }
}
