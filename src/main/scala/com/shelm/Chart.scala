package com.shelm

import java.net.URI

import io.circe.{Decoder, DecodingFailure, HCursor}

case class Chart(
  apiVersion: String,
  name: String,
  version: String,
  kubeVersion: Option[String],
  description: Option[String],
  tpe: Option[ChartType],
  keywords: Option[List[String]],
  home: Option[URI],
  sources: Option[List[URI]],
  dependencies: Option[List[ChartDependency]],
  maintainers: Option[List[ChartMaintainer]],
  icon: Option[URI],
  appVersion: Option[String],
  deprecated: Option[Boolean],
  annotations: Option[ChartAnnotations],
)
object Chart {
  implicit val decoder: Decoder[Chart] = (c: HCursor) =>
    for {
      apiVersion <- c.get[String]("apiVersion")
      name <- c.get[String]("name")
      version <- c.get[String]("version")
      kubeVersion <- c.get[Option[String]]("kubeVersion")
      description <- c.get[Option[String]]("description")
      tpe <- c.get[Option[ChartType]]("type")
      keywords <- c.get[Option[List[String]]]("keywords")
      home <- c.get[Option[String]]("home").map(_.map(URI.create))
      sources <- c.get[Option[List[String]]]("sources").map(_.map(_.map(URI.create)))
      dependencies <- c.get[Option[List[ChartDependency]]]("dependencies")
      maintainers <- c.get[Option[List[ChartMaintainer]]]("maintainers")
      icon <- c.get[Option[String]]("icon").map(_.map(URI.create))
      appVersion <- c.get[Option[String]]("appVersion")
      deprecated <- c.get[Option[Boolean]]("deprecated")
      annotations <- c.get[Option[ChartAnnotations]]("annotations")
    } yield Chart(
      apiVersion,
      name,
      version,
      kubeVersion,
      description,
      tpe,
      keywords,
      home,
      sources,
      dependencies,
      maintainers,
      icon,
      appVersion,
      deprecated,
      annotations,
    )
}

case class ChartDependency(
  name: String,
  version: String,
  repository: URI,
  condition: Option[String],
  tags: Option[List[String]],
  enabled: Option[Boolean],
  importValues: Option[List[String]],
  alias: Option[String],
)
object ChartDependency {
  implicit val decoder: Decoder[ChartDependency] = (c: HCursor) =>
    for {
      name <- c.get[String]("name")
      version <- c.get[String]("version")
      repository <- c.get[String]("repository").map(URI.create)
      condition <- c.get[Option[String]]("condition")
      tags <- c.get[Option[List[String]]]("tags")
      enabled <- c.get[Option[Boolean]]("enabled")
      importValues <- c.get[Option[List[String]]]("import-values")
      alias <- c.get[Option[String]]("alias")
    } yield ChartDependency(
      name,
      version,
      repository,
      condition,
      tags,
      enabled,
      importValues,
      alias,
    )
}

case class ChartMaintainer(name: String, email: Option[String], url: Option[URI])
object ChartMaintainer {
  implicit val decoder: Decoder[ChartMaintainer] = (c: HCursor) =>
    for {
      name <- c.get[String]("name")
      email <- c.get[Option[String]]("email")
      url <- c.get[Option[String]]("url").map(r => r.map(URI.create))
    } yield ChartMaintainer(name, email, url)
}

case class ChartAnnotations(annotations: Map[String, String])
object ChartAnnotations {
  implicit val decoder: Decoder[ChartAnnotations] = Decoder.decodeMap[String, String].map(ChartAnnotations(_))
}

sealed abstract class ChartType(val tpe: String)
object ChartType {
  case object Application extends ChartType("application")
  case object Library extends ChartType("library")

  private val caseObjects = Vector(Application, Library)

  implicit val decoder: Decoder[ChartType] = (c: HCursor) =>
    c.value.asString
      .flatMap(s => caseObjects.find(_.tpe == s))
      .toRight(DecodingFailure(s"Wrong Chart type, must be one of $caseObjects", Nil))
}
