package com.shelm

import java.io.FileReader

import io.circe.yaml
import io.circe.Json

import sbt.{Def, io, _}
import Keys._

object HelmPlugin extends AutoPlugin {
  object autoImport {
    val Helm = config("helm")

    val helmChart = settingKey[File]("Chart directory")
    val helmChartName = settingKey[String]("Chart name")
    val helmChartVersion = settingKey[String]("Chart version")
    val helmDestination = settingKey[File]("Chart destination directory")
    val helmPackage = taskKey[File]("Create Helm package")

    lazy val baseHelmSettings: Seq[Setting[_]] = Seq(
      helmDestination := target.value,
      helmChartName := readChartName(),
      helmChartVersion := readChartVersion(),
      helmPackage := buildChart(
        helmChart.value,
        helmChartName.value,
        helmChartVersion.value,
        helmDestination.value,
        streams.value.log
      )
    )
  }
  private val ChartYaml = "Chart.yaml"

  import autoImport._

  private lazy val chartYaml: Json = yaml.parser.parse(new FileReader(helmChart.value / ChartYaml)) match {
    case Right(json) => json
    case Left(err) => throw err
  }

  private[this] def readChartName(chartDir: File): String = chartYaml.as[Map[String, String]]

  private[this] def readChartVersion(chartDir: File): String = ???

  private[this] def buildChart(chartDir: File, chartName: String, chartVersion: String, targetDir: File, log: Logger): File = {
    log.info("Creating Helm Package")
    val cmd = s"helm package -d $targetDir"
    sys.process.Process(
      command = cmd,
      cwd = Some(chartDir)
    ) ! log match {
      case 0 => targetDir / s"$chartName-$chartVersion.tgz"
      case exitCode => sys.error(s"The command: $cmd, failed with: $exitCode")
    }
  }

  override lazy val projectSettings: Seq[Setting[_]] = inConfig(Helm)(baseHelmSettings)
}
