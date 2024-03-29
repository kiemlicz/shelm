package io.github.kiemlicz.shelm

import io.circe.{Decoder, Encoder, Json}

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
    * @param uri  repo URI
    * @param auth repo auth settings (basic, token)
    */
  case class RemoteRepository(
    chartName: ChartName,
    uri: URI,
    auth: ChartRepositoryAuth,
    chartVersion: Option[String] = None,
  ) extends ChartLocation

  /**
    * Remote OCI registry, command: `helm registry login` must already be done
    * `helm` binary doesn't handle registry login during pull
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

object ChartRepositoryName {
  implicit val decoder: Decoder[ChartRepositoryName] = _.downField("name").as[String].map(ChartRepositoryName(_))
  implicit val encoder: Encoder[ChartRepositoryName] = crn => Json.obj(("name", Json.fromString(crn.name)))
}

/**
  * `helm repo list -o yaml` single list entry
  */
case class RepoListEntry(chartRepositoryName: ChartRepositoryName, uri: URI)

object RepoListEntry {
  implicit val decoder: Decoder[RepoListEntry] = c => for {
    name <- c.as[ChartRepositoryName]
    uri <- c.downField("url").as[URI]
  } yield RepoListEntry(name, uri)

  implicit val encoder: Encoder[RepoListEntry] = rle => Json.obj(
    ("name", Json.fromString(rle.chartRepositoryName.name)),
    ("url", Json.fromString(rle.uri.toString)),
  )
}

case class AuthEntry(auth: Option[String])

object AuthEntry {
  implicit val d: Decoder[AuthEntry] = _.downField("auth").as[Option[String]].map(AuthEntry(_))
}

case class Auths(auths: Map[URI, Option[AuthEntry]])

object Auths {
  implicit val d: Decoder[Auths] = _.downField("auths").as[Map[URI, Option[AuthEntry]]].map(Auths(_))
}

/**
  * Registry/Repository which need prior setup
  */
trait ChartHosting {
  def uri(): URI

  def auth(): ChartRepositoryAuth
}

/**
  * Using Helm's naming scheme
  * 'old-style': repository
  */
trait Repository extends ChartHosting {
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
) extends Repository

case class ChartMuseumRepository(
  name: ChartRepositoryName,
  uri: URI,
  auth: ChartRepositoryAuth = ChartRepositoryAuth.NoAuth,
) extends Repository

object ChartMuseumRepository {
  def forcePushUrl(uri: URI): URI = URI.create(s"${uri.toString}?force")
}

/**
  * OCI Chart Registry, requires prior `helm registry login`
  * https://github.com/opencontainers/distribution-spec/blob/v1.0.1/spec.md
  */
case class OciChartRegistry(
  uri: URI,
  auth: ChartRepositoryAuth = ChartRepositoryAuth.NoAuth,
  loginCommandDropsScheme: Boolean = true
) extends ChartHosting {
  require(uri.getScheme.startsWith("oci"), "OciChartRegistry URI must start with oci:// scheme")

  def loginUri: URI = if (loginCommandDropsScheme) new URI(uri.toString.replaceFirst("^oci://", "")) else uri
}

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
    */
  case class UserPassword(user: String, password: String) extends ChartRepositoryAuth {
    override def toString: String = {
      s"$user,EDITED"
    }
  }

  case class Cert(certFile: File, keyFile: File, ca: Option[File]) extends ChartRepositoryAuth

  /**
    * @param token Long-lived token
    */
  case class Bearer(token: String, username: Option[String]) extends ChartRepositoryAuth {
    override def toString: String = {
      s"$username,EDITED"
    }
  }
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
  lintSettings: LintSettings = LintSettings()
)

/**
  *
  * @param fatalLint   break the build if lint fails
  * @param kubeVersion --kube-version option to validate against given Kubernetes API version
  * @param strictLint  fatal warning
  */
case class LintSettings(
  fatalLint: Boolean = true,
  kubeVersion: Option[SemVer2] = None,
  strictLint: Boolean = false
)

object ChartSettings {
  final val ChartYaml = "Chart.yaml"
  final val ValuesYaml = "values.yaml"
  final val DependenciesPath = "charts"
}

case class PackagedChartInfo(chartName: ChartName, version: SemVer2, location: File)
