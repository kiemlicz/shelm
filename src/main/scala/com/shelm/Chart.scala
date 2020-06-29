package com.shelm

import java.net.URI

import com.avsystem.commons.ISeq
import com.avsystem.commons.misc.{NamedEnumCompanion, Opt}
import com.avsystem.commons.serialization.HasGenCodec

case class Chart(apiVersion: String,
                 name: String,
                 version: String,
                 kubeVersion: Opt[String],
                 description: Opt[String],
                 `type`: Opt[String],
                 keywords: List[String],
                 home: Opt[String],
                 sources: List[URI],
                 dependencies: List[ChartDependency],
                 maintainers: List[ChartMaintainer],
                 icon: Opt[URI],
                 appVersion: Opt[String],
                 deprecated: Opt[Boolean],
                 annotations: ChartAnnotations) extends HasGenCodec[Chart]
//todo
case class ChartDependency()
case class ChartMaintainer()
case class ChartAnnotations()

sealed abstract class ChartType(`type`: String)
object ChartType extends NamedEnumCompanion[ChartType] {
  case object Application extends ChartType("application")
  case object Library extends ChartType("library")
  override val values: ISeq[ChartType] = caseObjects
}
