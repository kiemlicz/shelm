package com.shelm

import java.net.URI

import io.circe.{Decoder, Encoder}

case class Chart(apiVersion: String,
                 name: String,
                 version: String,
                 kubeVersion: Option[String],
                 description: Option[String],
                 `type`: Option[String],
                 keywords: List[String],
                 home: Option[String],
                 sources: List[URI],
                 dependencies: List[ChartDependency],
                 maintainers: List[ChartMaintainer],
                 icon: Option[URI],
                 appVersion: Option[String],
                 deprecated: Option[Boolean],
                 annotations: ChartAnnotations)
object Chart {
  implicit val decoder: Decoder[Chart] = ???
}

case class ChartDependency()
object ChartDependency {
  implicit val decoder: Decoder[ChartDependency] = ???
}
case class ChartMaintainer()
object ChartMaintainer {
  implicit val decoder: Decoder[ChartMaintainer] = ???
}
case class ChartAnnotations()
object ChartAnnotations {
  implicit val decoder: Decoder[ChartAnnotations] = ???
}

sealed abstract class ChartType(`type`: String)
object ChartType {
  case object Application extends ChartType("application")
  case object Library extends ChartType("library")

  implicit val decoder: Decoder[ChartType] = ???
}
