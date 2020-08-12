package com.shelm

import java.io.File

import io.circe.Json
import sbt.URI

sealed trait ChartLocation
object ChartLocation {
  case class Local(location: File) extends ChartLocation
  case class Remote(location: URI) extends ChartLocation
  case class Repository(repo: String, name: String) extends ChartLocation
}

/**
  *
 * @param destination
  * @param chartUpdate Chart.yaml generation function, receives internal Chart.yaml
  * @param dependencyUpdate
  * @param includeFiles
  * @param yamlsToMerge
  * @param valueOverrides
  */
case class ChartPackagingSettings(
  chartLocation: ChartLocation,
  destination: File,
  chartUpdate: Chart => Chart = identity,
  dependencyUpdate: Boolean = true,
  includeFiles: Seq[(File, String)] = Seq.empty,
  yamlsToMerge: Seq[(File, String)] = Seq.empty,
  valueOverrides: Option[Json] => Seq[Json] = _ => Seq.empty,
)
object ChartPackagingSettings {
  final val ChartYaml = "Chart.yaml"
  final val ValuesYaml = "values.yaml"
}
