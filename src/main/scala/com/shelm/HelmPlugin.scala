package com.shelm

import java.io.FileReader

import io.circe._, io.circe.syntax._
import io.circe.generic._
import io.circe.{Json, yaml}
import sbt.Keys._
import sbt._

object HelmPlugin extends AutoPlugin {
  override def trigger = noTrigger

  object autoImport {
    val Helm: Configuration = config("helm")
    // format: off
    lazy val chartDirectory = settingKey[File]("Chart directory")
    lazy val chartName = settingKey[String]("Chart name")
    lazy val chartVersion = settingKey[String]("Chart version")
    lazy val chartAppVersion = settingKey[Option[String]]("Chart appVersion")
    lazy val chartSetAppVersion = settingKey[Boolean]("If Chart appVersion should be set from version.value")
    lazy val chartYaml = settingKey[Chart]("Parsed Chart.yaml file")
    lazy val packageDestination = settingKey[File]("Chart destination directory (-d)")
    lazy val packageDependencyUpdate = settingKey[Boolean]("Chart dependency update before package (-u)")
    lazy val packageIncludeFiles = settingKey[Seq[(File, String)]]("List of files or directories to copy (override=true) to specified path relative to Chart root")
    lazy val packageMergeYamls = settingKey[Seq[(File, String)]]("List of YAML files to merge with existing ones, runs after include.")
    lazy val packageValueOverrides = settingKey[Seq[Map[String, Any]]]("Programmatic way to override any values.yaml setting")

    lazy val prepare = taskKey[File]("Copy all includes into Chart directory, return Chart directory")
    lazy val lint = taskKey[File]("Lint Helm Chart")
    lazy val create = taskKey[File]("Create Helm Chart")
    // format: on

    lazy val baseHelmSettings: Seq[Setting[_]] = Seq(
      chartYaml := resultOrThrow(
        yaml.parser.parse(new FileReader(chartDirectory.value / ChartYaml)).flatMap(_.as[Chart])
      ),
      chartName := chartYaml.value.name,
      chartVersion := chartYaml.value.version,
      chartAppVersion := Some(version.value),
      chartSetAppVersion := true,
      packageDestination := target.value,
      packageDependencyUpdate := true,
      packageIncludeFiles := Seq.empty,
      packageValueOverrides := Seq.empty,
      packageMergeYamls := Seq.empty,
      prepare := {
        val tempChartDir = target.value / chartName.value
        val updatedChartYaml = chartYaml.value.copy(
          name = chartName.value,
          version = chartVersion.value,
          appVersion = if (chartSetAppVersion.value) chartAppVersion.value else chartYaml.value.appVersion,
        )
        IO.copyDirectory(chartDirectory.value, tempChartDir, overwrite = true)
        packageIncludeFiles.value.foreach {
          case (src, d) =>
            val dst = tempChartDir / d
            if (src.isDirectory) IO.copyDirectory(src, dst, overwrite = true)
            else IO.copyFile(src, dst)
        }
        packageMergeYamls.value.foreach {
          case (overrides, onto) =>
            val tempChartDir = target.value / chartName.value
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
        mergeOverrides(packageValueOverrides.value).foreach { valuesOverride: Map[String, Any] =>
          val valuesFile = tempChartDir / ValuesYaml
          IO.write(
            valuesFile,
            if (valuesFile.exists())
              resultOrThrow(
                yaml.parser
                  .parse(new FileReader(valuesFile))
                  .map(onto => yaml.printer.print(valuesOverride.asJson))
              )
            else yaml.printer.print(valuesOverride.asJson),
          )
        }
        IO.write(tempChartDir / ChartYaml, yaml.printer.print(updatedChartYaml.asJson))
        cleanFiles ++= Seq(tempChartDir)
        tempChartDir
      },
      lint := lintChart(prepare.value, streams.value.log),
      create := {
        val linted = lint.value
        buildChart(
          linted,
          chartName.value,
          chartVersion.value,
          packageDestination.value,
          packageDependencyUpdate.value,
          streams.value.log,
        )
      },
    )
  }
  private val ChartYaml = "Chart.yaml"
  private val ValuesYaml = "values.yaml"

  import autoImport._

  private[this] def lintChart(chartDir: File, log: Logger): File = {
    log.info("Linting Helm Package")
    val cmd = s"helm lint $chartDir"
    sys.process.Process(command = cmd) ! log match {
      case 0 => chartDir
      case exitCode => sys.error(s"The command: $cmd, failed with: $exitCode")
    }
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
    sys.process.Process(command = cmd) ! log match {
      case 0 => targetDir / s"$chartName-$chartVersion.tgz"
      case exitCode => sys.error(s"The command: $cmd, failed with: $exitCode")
    }
  }

  private[this] def mergeOverrides(overrides: Seq[Map[String, Any]], listMerge: Boolean = true): Option[Map[String, Any]] = {

    def mergeVal(src: Any, dst: Any): Any = (src ,dst) match {
      case (src: Seq[_], dst: Seq[_]) if listMerge => src ++ dst
      case (src: Seq[_], dst: Seq[_]) => dst
      case (src: Map[String, _], dst: Map[String, _]) => merge(src,dst)
    }

    def merge(src: Map[String, Any], dst: Map[String, Any]): Map[String, Any] = {
      (src.keySet ++ dst.keySet).map {k => src.get(k) -> dst.get(k) match {
        case (Some(srcVal), Some(dstVal)) => k -> mergeVal(srcVal, dstVal)
        case ((Some(srcVal), None)) => k -> srcVal
        case (None, Some(dstVal)) => k -> dstVal
        case _ => throw new IllegalStateException()
      }}.toMap
    }

    val merged = overrides.foldLeft(Json.Null)(_.deepMerge(_))
    if (overrides.isEmpty) None else Some(merged)
  }

  override lazy val projectSettings: Seq[Setting[_]] =
    inConfig(Helm)(baseHelmSettings)

  override def projectConfigurations: Seq[Configuration] = Seq(Helm)

  private[this] def resultOrThrow[R](r: Either[Throwable, R]): R = r match {
    case Right(value) => value
    case Left(err) => throw err
  }
}
