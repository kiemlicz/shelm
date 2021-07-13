package io.github.kiemlicz.shelm

import io.circe.Json
import sbt.librarymanagement.VersionNumber

import java.io.File
import java.net.URI

/**
  * Pointer to the Chart location with all required information to perform download
  */
sealed trait ChartLocation {
  /**
    * Exact Chart name (Chart.yaml's name)
    */
  def chartName: String
}

object ChartLocation {

  /**
    * Chart on local filesystem, either packaged (*.tgz or not)
    *
    * @param location Chart root dir
    */
  case class Local(chartName: String, location: File) extends ChartLocation

  object Local {
    def apply(location: File): Local = Local(location.getName, location)
  }

  /**
    * Link for packaged *.tgz
    *
    * @param location remote URI to packaged chart (*.tgz)
    */
  case class Remote(chartName: String, location: URI) extends ChartLocation

  /**
    * `helm repo add`ed repository
    *
    * @param repository   repository name as configured on host where Helm binary runs
    * @param chartVersion version to download, latest available otherwise (mind that 'latest' means: latest from **last** `helm repo update`)
    */
  case class AddedRepository(
    chartName: String,
    repository: ChartRepositoryName,
    chartVersion: Option[String] = None,
  ) extends ChartLocation

  /**
    * Any remote repository
    *
    * @param uri      repo URI
    * @param settings mainly auth settings
    */
  case class RemoteRepository(
    chartName: String,
    uri: URI,
    settings: ChartRepositorySettings,
    chartVersion: Option[String] = None,
  ) extends ChartLocation
}

case class ChartRepositoryName(name: String) extends AnyVal

/**
  * Helm Chart Repository
  */
case class ChartRepository(
  name: ChartRepositoryName,
  uri: URI,
  settings: ChartRepositorySettings = ChartRepositorySettings.NoAuth,
)

sealed trait ChartRepositorySettings

object ChartRepositorySettings {
  case object NoAuth extends ChartRepositorySettings

  case class UserPassword(user: String, password: String) extends ChartRepositorySettings

  case class Cert(certFile: File, keyFile: File, ca: Option[File]) extends ChartRepositorySettings
}

/**
  * Main single Chart packaging settings
  *
  * @param chartLocation    Helm Chart location (either local or remote)
  * @param destination      Where to put (re)packaged Chart: destination / chartname.tgz
  * @param chartUpdate      Chart.yaml generation function, receives currently read Chart.yaml
  * @param dependencyUpdate perform `helm dependency update` before `helm package` (default: true)
  * @param fatalLint        fail if `helm lint` fails (default: true)
  * @param metadata         optional metadata mainly to be used to distinguish between same Chart re-packing in chartMappings
  */
case class ChartPackagingSettings(
  chartLocation: ChartLocation,
  destination: File,
  chartUpdate: Chart => Chart = identity,
  dependencyUpdate: Boolean = true,
  fatalLint: Boolean = true,
  metadata: Option[String] = None
)

/**
  * File mappings that will be added to the Chart (configs, secrets, programmatic data, etc.)
  *
  * @param chartSettings  main chart settings
  * @param includeFiles   list of file mappings which will be present in Chart (sbt-native-packager-a-like)
  * @param yamlsToMerge   list of yaml files that will be merged with currently present in Chart or added
  * @param valueOverrides programmatic overrides (takes priority over `yamlsToMerge`)
  */
case class ChartMappings(
  chartSettings: ChartPackagingSettings,
  includeFiles: Seq[(File, String)] = Seq.empty,
  yamlsToMerge: Seq[(File, String)] = Seq.empty,
  valueOverrides: Option[Json] => Seq[Json] = _ => Seq.empty,
)

object ChartPackagingSettings {
  final val ChartYaml = "Chart.yaml"
  final val ValuesYaml = "values.yaml"
}

case class ChartRepositoriesSettings(
  repositories: Seq[ChartRepository] = Seq.empty,
  update: Boolean = false,
)

case class PackagedChartInfo(name: String, version: VersionNumber, location: File)
