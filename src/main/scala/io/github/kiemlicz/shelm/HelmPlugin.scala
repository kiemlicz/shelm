package io.github.kiemlicz.shelm

import io.circe.syntax.EncoderOps
import io.circe.{Json, yaml}
import io.github.kiemlicz.shelm.ChartRepositorySettings.{Cert, NoAuth, UserPassword}
import io.github.kiemlicz.shelm.ChartSettings.{ChartYaml, DependenciesPath, ValuesYaml}
import io.github.kiemlicz.shelm.exception.HelmCommandException
import sbt.Keys._
import sbt.librarymanagement.PublishConfiguration
import sbt.{Def, ModuleDescriptorConfiguration, ModuleID, Resolver, UpdateLogging, _}

import java.io.FileReader
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import scala.annotation.tailrec
import scala.concurrent.duration.{FiniteDuration, SECONDS}

object HelmPlugin extends AutoPlugin {
  override def trigger = noTrigger

  object autoImport {
    val Helm: Configuration = config("helm")

    lazy val repositories = settingKey[Seq[ChartRepository]]("Additional Repositories settings")
    lazy val shouldUpdateRepositories = settingKey[Boolean]("Perform `helm repo update` at the helm:prepare beginning")
    lazy val chartSettings = settingKey[Seq[ChartSettings]]("All per-Chart settings")

    lazy val helmVersion = taskKey[VersionNumber]("Local Helm binary version")
    lazy val addRepositories = taskKey[Seq[ChartRepository]]("Setup Helm Repositories. Idempotent operation")
    lazy val updateRepositories = taskKey[Unit]("Update Helm Repositories")
    lazy val chartMappings = taskKey[ChartSettings => ChartMappings]("All per-Chart mappings")
    lazy val prepare = taskKey[Seq[(File, ChartMappings)]]("Download Chart if not present locally, copy all includes into Chart directory, return Chart directory")
    lazy val lint = taskKey[Seq[(File, ChartMappings)]]("Lint Helm Chart")
    lazy val packagesBin = taskKey[Seq[PackagedChartInfo]]("Create Helm Charts")

    lazy val baseHelmSettings: Seq[Setting[_]] = Seq(
      repositories := Seq.empty,
      shouldUpdateRepositories := false,
      chartSettings := Seq.empty[ChartSettings],
      helmVersion := {
        val cmd = "helm version --template {{.Version}}"
        startProcess(cmd) match {
          case HelmProcessResult.Success(output) => VersionNumber(output.stdOut.replaceFirst("^v", ""))
          case HelmProcessResult.Failure(exitCode, output) => throw new HelmCommandException(output, exitCode)
        }
      },
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
      chartMappings := { s => ChartMappings(s, target.value) },
      prepare := {
        val log = streams.value.log
        val helmVer = helmVersion.value
        helmVer match {
          case VersionNumber(Seq(major, _, _), _, _) if major >= 3 =>
          case _ => sys.error(s"Cannot assert Helm version (must be at least 3.0.0): $helmVer")
        }

        val _ = Def.taskIf {
          if (shouldUpdateRepositories.value) updateRepositories.value
          else ()
        }.value

        val mappings = chartMappings.value //must be outside of lambda
        val settings = chartSettings.value
        settings.map(mappings).zipWithIndex.map { case (mappings, idx) =>
          val tempChartDir = ChartDownloader.download(
            mappings.settings.chartLocation,
            target.value / s"${mappings.settings.chartLocation.chartName.name}-$idx", log
          )
          if (mappings.dependencyUpdate) {
            updateDependencies(tempChartDir, log)
            (tempChartDir ** "*.tgz").get()
              .foreach { f =>
                ChartDownloader.extractArchive(f.toURI, tempChartDir / DependenciesPath)
                IO.delete(f)
              }
          }
          val chartYaml = readChart(tempChartDir / ChartYaml)
          val updatedChartYaml = mappings.chartUpdate(chartYaml)
          mappings.includeFiles.foreach { case (src, d) =>
            val dst = tempChartDir / d
            if (src.isDirectory) IO.copyDirectory(src, dst, overwrite = true)
            else IO.copyFile(src, dst)
          }
          mappings.yamlsToMerge.foreach { case (overrides, onto) =>
            val dst = tempChartDir / onto
            if (dst.exists()) {
              val onto = yaml.parser.parse(new FileReader(dst))
              val result = yaml.parser.parseDocuments(new FileReader(overrides)).foldLeft(onto) { (onto, parsed) =>
                parsed.flatMap(parsed => onto.map(onto => onto.deepMerge(parsed)))
              }.map(yaml.printer.print)
              IO.write(dst, resultOrThrow(result))
            } else IO.copyFile(overrides, dst)
          }
          val valuesFile = tempChartDir / ValuesYaml
          val valuesJson = if (valuesFile.exists()) yaml.parser.parse(new FileReader(valuesFile)).toOption else None
          val overrides = mergeOverrides(mappings.valueOverrides(valuesJson))
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
          cleanFiles ++= Seq(tempChartDir) //todo is it thread safe? After all this can be run concurrently
          (tempChartDir, mappings)
        }
      },
      lint := {
        val log = streams.value.log
        prepare.value.map { case (chartDir, m: ChartMappings) =>
          (lintChart(chartDir, m.fatalLint, log), m)
        }
      },
      packagesBin := {
        lint.value.map { case (linted, m: ChartMappings) =>
          val chartYaml = readChart(linted / ChartYaml)
          val location = buildChart(
            linted,
            chartYaml.name,
            chartYaml.version,
            m.destination,
            streams.value.log,
          )
          PackagedChartInfo(chartYaml.name, SemVer2(chartYaml.version), location)
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

  private[this] def updateDependencies(chartDir: File, log: Logger): Unit = {
    log.info("Updating Helm Chart's dependencies")
    retrying(s"helm dependency update $chartDir --skip-refresh", log) // due to potential parallel runs... https://github.com/helm/helm/issues/4463
  }

  private[this] def lintChart(chartDir: File, fatalLint: Boolean, log: Logger): File = {
    log.info("Linting Helm Package")
    val cmd = s"helm lint $chartDir"
    startProcess(cmd) match {
      case HelmProcessResult.Failure(exitCode, output) if fatalLint => throw new HelmCommandException(output, exitCode)
      case _ => chartDir
    }
  }

  private[shelm] def pullChart(options: String, log: Logger): Unit = {
    val cmd = s"helm pull $options"
    retrying(cmd, log)
  }

  private[this] def buildChart(
    chartDir: File,
    chartName: ChartName,
    chartVersion: String,
    targetDir: File,
    log: Logger,
  ): File = {
    val dest = s" -d $targetDir"
    val cmd = s"helm package$dest $chartDir"
    val output = targetDir / s"${chartName.name}-$chartVersion.tgz"
    log.info(s"Creating Helm Package: $cmd")
    retrying(cmd, log)
    output
  }

  /**
    * Retry given command
    * That method exists only due to: https://github.com/helm/helm/issues/2258
    *
    * @param cmd shell command that will potentially be retried
    * @param n   number of tries
    */
  private[this] def retrying(cmd: String, sbtLogger: Logger, n: Int = 3): Unit = {
    val initialSleep = FiniteDuration(1, SECONDS)
    val backOff = 2

    @tailrec
    def go(n: Int, result: HelmProcessResult, sleep: FiniteDuration): Unit = result match {
      case HelmProcessResult.Success(output) =>
        if (output.entireOutput.nonEmpty)
          sbtLogger.info(s"Helm command ('$cmd') success, output:\n${output.entireOutput}")
        else
          sbtLogger.info(s"Helm command ('$cmd') success")
      case HelmProcessResult.Failure(exitCode, output) if n > 0 =>
        sbtLogger.warn(
          s"Couldn't perform: $cmd (exit code: $exitCode), failed with: $output, retrying in: $sleep"
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
    case Cert(certFile, keyFile, ca) => s"--cert-file ${certFile.getAbsolutePath} --key-file ${
      keyFile.getAbsolutePath
    } ${ca.map(ca => s"--ca-file $ca").getOrElse("")}"
  }

  override lazy val projectSettings: Seq[Setting[_]] =
    inConfig(Helm)(baseHelmSettings)

  override def projectConfigurations: Seq[Configuration] = Seq(Helm)
}

object HelmPublishPlugin extends AutoPlugin {

  import HelmPlugin.autoImport._

  override def requires: HelmPlugin.type = HelmPlugin

  lazy val baseHelmPublishSettings: Seq[Setting[_]] = Seq(
    artifacts := Seq.empty,
    packagedArtifacts := Map.empty,
    projectID := ModuleID(organization.value, name.value, version.value),
    moduleSettings := ModuleDescriptorConfiguration(projectID.value, projectInfo.value)
      .withScalaModuleInfo(scalaModuleInfo.value),
    ivyModule := {
      val ivy = ivySbt.value
      new ivy.Module(moduleSettings.value)
    },
    publishConfiguration := PublishConfiguration()
      .withResolverName(Classpaths.getPublishTo(publishTo.value).name)
      .withArtifacts(packagedArtifacts.value.toVector)
      .withChecksums(checksums.value.toVector)
      .withOverwrite(isSnapshot.value)
      .withLogging(UpdateLogging.DownloadOnly),
    publishLocalConfiguration := PublishConfiguration()
      .withResolverName("local")
      .withArtifacts(packagedArtifacts.value.toVector)
      .withChecksums(checksums.value.toVector)
      .withOverwrite(isSnapshot.value)
      .withLogging(UpdateLogging.DownloadOnly),
    publishM2Configuration := PublishConfiguration()
      .withResolverName(Resolver.mavenLocal.name)
      .withArtifacts(packagedArtifacts.value.toVector)
      .withChecksums(checksums.value.toVector)
      .withOverwrite(isSnapshot.value)
      .withLogging(UpdateLogging.DownloadOnly),
  )

  private[this] def addPackage(
    helmPackageTask: TaskKey[Seq[PackagedChartInfo]],
    extension: String,
    classifier: Option[String] = None,
  ): Seq[Setting[_]] =
    Seq(
      artifacts ++= chartSettings.value.map { s =>
        Artifact(
          s.chartLocation.chartName.name,
          extension,
          extension,
          classifier,
          Vector.empty,
          None
        )
      },
      /*
      the `artifacts` is a SettingKey, since the Chart version is known in the Task run, can't set this in settings
       */
      packagedArtifacts ++= artifacts.value
        .zip(helmPackageTask.value)
        .map {
          case (artifact, packagedChart) =>
            artifact.withExtraAttributes(
              artifact.extraAttributes ++ Map(
                "chartVersion" -> packagedChart.version.toString,
                "chartMajor" -> packagedChart.version.major.toString,
                "chartMinor" -> packagedChart.version.minor.toString,
                "chartPatch" -> packagedChart.version.patch.toString,
                "chartName" -> packagedChart.chartName.name,
              )
            ) -> packagedChart.location
        }.toMap,
    )

  /**
    * Saves scoped `publishTo` (`Helm / publishTo)` into `otherResolvers`
    * This way only scoped publishTo is required to be set
    */
  private[this] def addResolver(config: Configuration): Seq[Setting[_]] =
    Seq(otherResolvers ++= (config / publishTo).value.toSeq)

  override lazy val projectSettings: Seq[Setting[_]] =
    inConfig(Helm)(
      Classpaths.ivyPublishSettings
        ++ baseHelmPublishSettings
        ++ addPackage(packagesBin, "tgz")
    ) ++ addResolver(Helm)
}
