package com.shelm

import java.io.FileReader

import io.circe.syntax._
import io.circe.yaml
import sbt.Keys._
import sbt._

object HelmPlugin extends AutoPlugin {
  object autoImport {
    val Helm = config("helm")

    val chartDirectory = settingKey[File]("Chart directory")
    val chartName = settingKey[String]("Chart name")
    val chartVersion = settingKey[String]("Chart version")
    val chartAppVersion = settingKey[Option[String]]("Chart appVersion")
    val chartSetAppVersion = settingKey[Boolean]("If Chart appVersion should be set from version.value")
    val packageDestination = settingKey[File]("Chart destination directory (-d)")
    val packageDependencyUpdate = settingKey[Boolean]("Chart dependency update before package (-u)")
    val packageIncludeFiles = settingKey[Seq[(File, File)]](
      "List of files or directories to copy (override=true) to specified path relative to Chart root"
    )
    val packageMergeYamls =
      settingKey[Seq[(File, File)]]("List of YAML files to merge with existing ones, runs after include.")

    val preparePackage = taskKey[File]("Copy all includes into Chart directory, return Chart directory")
    val lintPackage = taskKey[File]("Lint Helm Chart")
    val createPackage = taskKey[File]("Create Helm Chart")

    private val chartYaml = settingKey[Chart]("Parsed Chart")

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
      packageIncludeFiles := Seq(),
      packageMergeYamls := Seq(),
      preparePackage := {
        val tempChartDir = target.value / chartName.value
        val updatedChartYaml = chartYaml.value.copy(
          name = chartName.value,
          version = chartVersion.value,
          appVersion = if(chartSetAppVersion.value) chartAppVersion.value else chartYaml.value.appVersion,
        )
        IO.copyDirectory(chartDirectory.value, tempChartDir, overwrite = true)
        packageIncludeFiles.value.foreach {
          case (src, d) =>
            val dst = tempChartDir / d.getPath
            if (src.isDirectory) IO.copyDirectory(src, dst, overwrite = true)
            else IO.copyFile(src, dst)
        }
        packageMergeYamls.value.foreach {
          case (overrides, onto) =>
            val tempChartDir = target.value / chartName.value
            val dst = tempChartDir / onto.getPath
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
        IO.write(tempChartDir / ChartYaml, yaml.printer.print(updatedChartYaml.asJson))
        cleanFiles ++= Seq(tempChartDir)
        tempChartDir
      },
      lintPackage := lintChart(preparePackage.value, streams.value.log),
      createPackage := {
        val linted = lintPackage.value
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

  import autoImport._

  private[this] def lintChart(chartDir: File, log: Logger): File = {
    log.info("Linting Helm Package")
    val cmd = s"helm lint ."
    sys.process.Process(command = cmd, cwd = Some(chartDir)) ! log match {
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
    log.info("Creating Helm Package")
    val opts = s"${if (dependencyUpdate) " -u" else ""}"
    val dest = s" -d $targetDir"
    val cmd = s"helm package$opts$dest ."
    sys.process.Process(command = cmd, cwd = Some(chartDir)) ! log match {
      case 0 => targetDir / s"$chartName-$chartVersion.tgz"
      case exitCode => sys.error(s"The command: $cmd, failed with: $exitCode")
    }
  }

  override def trigger = allRequirements

  override lazy val projectSettings: Seq[Setting[_]] =
    inConfig(Helm)(baseHelmSettings)

  private[this] def resultOrThrow[R](r: Either[Throwable, R]): R = r match {
    case Right(value) => value
    case Left(err) => throw err
  }
}
