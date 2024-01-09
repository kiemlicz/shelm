package io.github.kiemlicz.shelm

import io.circe.Json

import java.io.File
import java.net.URI

/**
  * Pointer to the Chart location with all required information to perform download
  */
sealed trait ChartLocation {
  /**
    * Exact Chart name (Chart.yaml's name)
    */
  def chartName: ChartName
}

object ChartLocation {

  /**
    * Chart on local filesystem, either packaged (*.tgz or not)
    *
    * @param location Chart root dir
    */
  case class Local(chartName: ChartName, location: File) extends ChartLocation

  object Local {
    def apply(location: File): Local = Local(ChartName(location.getName), location)
  }

  /**
    * Link for packaged *.tgz
    *
    * @param location remote URI to packaged chart (*.tgz)
    */
  case class Remote(chartName: ChartName, location: URI) extends ChartLocation

  /**
    * `helm repo add`ed repository
    * This is the legacy repository
    *
    * @param repository   repository name as configured on host where Helm binary runs
    * @param chartVersion version to download, latest available otherwise (mind that 'latest' means: latest from **last** `helm repo update`)
    */
  case class AddedRepository(
    chartName: ChartName,
    repository: ChartRepositoryName,
    chartVersion: Option[String] = None,
  ) extends ChartLocation

  /**
    * Any remote legacy repository
    *
    * @param uri      repo URI
    * @param settings mainly auth settings
    */
  case class RemoteRepository(
    chartName: ChartName,
    uri: URI,
    settings: ChartRepositoryAuth,
    chartVersion: Option[String] = None,
  ) extends ChartLocation

  /**
    *
    * @param chartName chart name within registry, will be appended as a last path segment
    * @param uri       registry URL, e.g. oci://registry-1.docker.io/kiemlicz
    */
  case class RemoteOciRegistry(
    chartName: ChartName,
    uri: URI,
    chartVersion: Option[String] = None,
  ) extends ChartLocation

}

case class ChartRepositoryName(name: String) extends AnyVal

/**
  * Registry/Repository which need prior setup
  */
trait ChartRepo {
  def uri(): URI

  def auth(): ChartRepositoryAuth
}

trait LegacyRepo extends ChartRepo {
  def name(): ChartRepositoryName
}

/**
  * Ivy repository, .e.g. Artifactory (uses HTTP PUT to upload artifacts)
  * org.apache.ivy.plugins.resolver.RepositoryResolver#put(org.apache.ivy.core.module.descriptor.Artifact, java.io.File, java.lang.String, boolean)
  * Helm Chart Repository (!= registry according to Helm doc)
  *
  * @param uri repo base url `helm repo add <uri>`
  */
case class IvyCompatibleHttpChartRepository(
  name: ChartRepositoryName,
  uri: URI,
  auth: ChartRepositoryAuth = ChartRepositoryAuth.NoAuth,
) extends LegacyRepo


case class ChartMuseumRepository(
  name: ChartRepositoryName,
  uri: URI,
  auth: ChartRepositoryAuth = ChartRepositoryAuth.NoAuth,
) extends LegacyRepo

/**
  * OCI Chart Registry, requires prior `helm registry login`
  * https://github.com/opencontainers/distribution-spec/blob/v1.0.1/spec.md
  */
case class OciChartRegistry(
  uri: URI,
  auth: ChartRepositoryAuth = ChartRepositoryAuth.NoAuth,
) extends ChartRepo

/**
  * Both legacy and OCI registry credentials
  */
sealed trait ChartRepositoryAuth

object ChartRepositoryAuth {
  case object NoAuth extends ChartRepositoryAuth

  /**
    * Depending on registry this can be either
    * - basic auth
    * - whathever helm repo add uses
    *
    * @param user
    * @param password
    */
  case class UserPassword(user: String, password: String) extends ChartRepositoryAuth

  case class Cert(certFile: File, keyFile: File, ca: Option[File]) extends ChartRepositoryAuth

  /**
    * @param token Long-lived token
    */
  case class Bearer(token: String) extends ChartRepositoryAuth
}

/**
  * Main single Chart packaging settings
  *
  * @param chartLocation Helm Chart location (either local or remote) to download from
  * @param metadata      optional metadata mainly to be used to distinguish between same Chart re-packing in chartMappings and yield proper `artifacts`
  */
case class ChartSettings(
  chartLocation: ChartLocation,
  metadata: Option[_] = None
)

/**
  * File mappings that will be added to the Chart (configs, secrets, programmatic data, etc.)
  *
  * @param settings         main chart settings
  * @param destination      Where to put (re)packaged Chart: destination / chartname.tgz
  * @param chartUpdate      Chart.yaml generation function, receives currently read Chart.yaml
  * @param includeFiles     list of file mappings which will be present in Chart (sbt-native-packager-a-like)
  * @param yamlsToMerge     list of yaml files that will be merged with currently present in Chart or added
  * @param valueOverrides   programmatic overrides (takes priority over `yamlsToMerge`)
  * @param dependencyUpdate perform `helm dependency update` before `helm package` (default: true)
  * @param fatalLint        fail if `helm lint` fails (default: true)
  */
case class ChartMappings(
  settings: ChartSettings,
  destination: File,
  chartUpdate: Chart => Chart = identity,
  includeFiles: Seq[(File, String)] = Seq.empty,
  yamlsToMerge: Seq[(File, String)] = Seq.empty,
  valueOverrides: Option[Json] => Seq[Json] = _ => Seq.empty,
  dependencyUpdate: Boolean = true,
  fatalLint: Boolean = true
)

object ChartSettings {
  final val ChartYaml = "Chart.yaml"
  final val ValuesYaml = "values.yaml"
  final val DependenciesPath = "charts"
}

case class PackagedChartInfo(chartName: ChartName, version: SemVer2, location: File)
