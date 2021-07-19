import _root_.io.circe.{Json, yaml}
import _root_.io.github.kiemlicz.shelm.HelmPlugin.autoImport.Helm
import _root_.io.github.kiemlicz.shelm._

import java.io.FileReader

lazy val assertGeneratedValues = taskKey[Unit]("Assert packageValueOverrides")
val cn = "overrides-chart"

lazy val root = (project in file("."))
  .enablePlugins(HelmPlugin)
  .settings(
    version := "0.1",
    scalaVersion := "2.13.3",
    Helm / chartSettings := Seq(
      ChartSettings(
        chartLocation = ChartLocation.Local(file(cn))
      )
    ),
    Helm / chartMappings := { s =>
      ChartMappings(
        s,
        destination = target.value,
        chartUpdate = _.copy(version = "3.2.3+meta.data", appVersion = Some("1.1")),
        includeFiles = Seq(
          file("config") -> "config"
        ),
        yamlsToMerge = Seq(
          file("values-override.yaml") -> "values.yaml"
        ),
        valueOverrides = {
          case Some(values) =>
            Seq(
              Json.fromFields(
                Iterable(
                  "replicaCount" -> Json.fromInt(
                    values.hcursor
                      .get[Int]("replicaCount")
                      .map(_ + 4)
                      .getOrElse(throw new IllegalStateException("Test fail: no replicaCount field found"))
                  ),
                  "long" -> Json.fromLong(450),
                  "dict" -> Json.fromFields(
                    Iterable(
                      "nest" -> Json.fromString("overrides")
                    )
                  ),
                )
              )
            )
          case _ => throw new IllegalStateException("test fail: no values.yaml found, they are required for this test")
        }
      )
    }
  )

assertGeneratedValues := {
  val tempChartValues = target.value / s"$cn-0" / cn / "values.yaml"
  yaml.parser.parse(new FileReader(tempChartValues)) match {
    case Right(json) =>
      val cursor = json.hcursor
      val expected: Set[String] = Set("replicaCount", "long", "dict")
      val all: Set[String] = cursor.keys.get.toSet
      if (!expected.forall(all.contains))
        throw new AssertionError(s"Test fail, values expected to contain: $expected, but: $all")
      cursor.get[Json]("image").map(j => j.hcursor.get[String]("tag"))
      val r = for {
        image <- cursor.get[Json]("image")
        replicaCount <- cursor.get[Int]("replicaCount")
        repository <- image.hcursor.get[String]("repository")
        tag <- image.hcursor.get[String]("tag")
      } yield repository == "nginx2" && tag == "someTag" && replicaCount == 6

      r match {
        case Right(true) =>
        case _ => throw new AssertionError(s"Test fail, wrong values.yaml settings detected")
      }
    case Left(err: Throwable) => throw err
  }
}
