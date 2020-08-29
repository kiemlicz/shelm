package com.shelm

import java.io.File

import io.circe.Json
import sbt.URI

sealed trait ChartLocation
object ChartLocation {

  /**
   * Chart on local filesystem
   * @param location Chart root dir
   */
  case class Local(location: File) extends ChartLocation

  /**
   * Link for packaged *.tgz
   * @param location remote URI to packaged chart (*.tgz)
   */
  case class Remote(location: URI) extends ChartLocation

  /**
   *
   * @param repo repository name as configured on host where Helm binary runs
   * @param name Chart name within given repository
   * @param chartVersion version to download, latest available otherwise (mind that 'latest' means: latest from **last** `helm repo update`)
   */
  case class Repository(repo: String, name: String, chartVersion: Option[String] = None) extends ChartLocation
}

/**
 * Main single Chart packaging settings
 * @param chartLocation Helm Chart location (either local or remote)
 * @param destination Where to put (re)packaged Chart: destination / chartname.tgz
 * @param chartUpdate Chart.yaml generation function, receives currently read Chart.yaml
 * @param dependencyUpdate perform `helm dependency update` before `helm package` (default: true)
 * @param fatalLint fail if `helm lint` fails (default: true)
 * @param includeFiles list of file mappings which will be present in Chart (sbt-native-packager-a-like)
 * @param yamlsToMerge list of yaml files that will be merged with currently present in Chart or added
 * @param valueOverrides programmatic overrides
 */
case class ChartPackagingSettings(
  chartLocation: ChartLocation,
  destination: File,
  chartUpdate: Chart => Chart = identity,
  dependencyUpdate: Boolean = true,
  fatalLint: Boolean = true,
  includeFiles: Seq[(File, String)] = Seq.empty,
  yamlsToMerge: Seq[(File, String)] = Seq.empty,
  valueOverrides: Option[Json] => Seq[Json] = _ => Seq.empty,
)
object ChartPackagingSettings {
  final val ChartYaml = "Chart.yaml"
  final val ValuesYaml = "values.yaml"
}
