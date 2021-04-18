package com.shelm

import io.circe._
import io.circe.syntax._

import java.net.URI

/**
 * Chart.yaml
 * https://helm.sh/docs/topics/charts/#the-chartyaml-file
 */
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

  import Common.{uriDecoder, uriEncoder}

  implicit val decoder: Decoder[Chart] = (c: HCursor) =>
    for {
      apiVersion <- c.get[String]("apiVersion")
      name <- c.get[String]("name")
      version <- c.get[String]("version")
      kubeVersion <- c.get[Option[String]]("kubeVersion")
      description <- c.get[Option[String]]("description")
      tpe <- c.get[Option[ChartType]]("type")
      keywords <- c.get[Option[List[String]]]("keywords")
      home <- c.get[Option[URI]]("home")
      sources <- c.get[Option[List[URI]]]("sources")
      dependencies <- c.get[Option[List[ChartDependency]]]("dependencies")
      maintainers <- c.get[Option[List[ChartMaintainer]]]("maintainers")
      icon <- c.get[Option[URI]]("icon")
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

  implicit val encoder: Encoder[Chart] = (chart: Chart) =>
    Json
      .obj(
        "apiVersion" -> chart.apiVersion.asJson,
        "name" -> chart.name.asJson,
        "version" -> chart.version.asJson,
        "kubeVersion" -> chart.kubeVersion.asJson,
        "description" -> chart.description.asJson,
        "type" -> chart.tpe.asJson,
        "keywords" -> chart.keywords.asJson,
        "home" -> chart.home.asJson,
        "sources" -> chart.sources.asJson,
        "dependencies" -> chart.dependencies.asJson,
        "maintainers" -> chart.maintainers.asJson,
        "icon" -> chart.icon.asJson,
        "appVersion" -> chart.appVersion.asJson,
        "deprecated" -> chart.deprecated.asJson,
        "annotations" -> chart.annotations.asJson,
      )
      .dropNullValues
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

  import Common.{uriDecoder, uriEncoder}

  implicit val decoder: Decoder[ChartDependency] = (c: HCursor) =>
    for {
      name <- c.get[String]("name")
      version <- c.get[String]("version")
      repository <- c.get[URI]("repository")
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
  implicit val encoder: Encoder[ChartDependency] = (chartDependency: ChartDependency) =>
    Json
      .obj(
        "name" -> chartDependency.name.asJson,
        "version" -> chartDependency.version.asJson,
        "repository" -> chartDependency.repository.asJson,
        "condition" -> chartDependency.condition.asJson,
        "tags" -> chartDependency.tags.asJson,
        "enabled" -> chartDependency.enabled.asJson,
        "importValues" -> chartDependency.importValues.asJson,
        "alias" -> chartDependency.alias.asJson,
      )
      .dropNullValues
}

case class ChartMaintainer(name: String, email: Option[String], url: Option[URI])

object ChartMaintainer {

  import Common.{uriDecoder, uriEncoder}

  implicit val decoder: Decoder[ChartMaintainer] = (c: HCursor) =>
    for {
      name <- c.get[String]("name")
      email <- c.get[Option[String]]("email")
      url <- c.get[Option[URI]]("url")
    } yield ChartMaintainer(name, email, url)

  implicit val encoder: Encoder[ChartMaintainer] = (chartMaintainer: ChartMaintainer) =>
    Json
      .obj(
        "name" -> chartMaintainer.name.asJson,
        "email" -> chartMaintainer.email.asJson,
        "url" -> chartMaintainer.url.asJson,
      )
      .dropNullValues
}

case class ChartAnnotations(annotations: Map[String, String])

object ChartAnnotations {
  implicit val decoder: Decoder[ChartAnnotations] = Decoder.decodeMap[String, String].map(ChartAnnotations(_))
  implicit val encoder: Encoder[ChartAnnotations] = (chartAnnotations: ChartAnnotations) =>
    chartAnnotations.annotations.asJson(Encoder.encodeMap[String, String])
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
  implicit val encoder: Encoder[ChartType] = (chartType: ChartType) => chartType.tpe.asJson
}

object Common {
  implicit val uriDecoder: Decoder[URI] = (c: HCursor) =>
    c.value.asString.map(URI.create).toRight(DecodingFailure("Cannot decode URI", Nil))
  implicit val uriEncoder: Encoder[URI] = (uri: URI) => uri.toString.asJson
}
