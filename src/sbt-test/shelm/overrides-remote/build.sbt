import java.io.FileReader
import _root_.io.github.shelm.ChartLocation._
import _root_.io.github.shelm.ChartLocation
import _root_.io.github.shelm.HelmPlugin.autoImport.Helm
import _root_.io.github.shelm.ChartPackagingSettings
import _root_.io.circe.{Json, yaml}

import java.net.URI

lazy val assertGeneratedValues = taskKey[Unit]("Assert packageValueOverrides")
val cn = "salt"

lazy val root = (project in file("."))
  .enablePlugins(HelmPlugin)
  .settings(
    version := "0.1",
    scalaVersion := "2.13.3",
    Helm / chartSettings := Seq(
      ChartPackagingSettings(
        chartLocation = ChartLocation.Remote("salt", URI.create("https://github.com/kiemlicz/ambassador/raw/gh-pages/salt-2.1.2.tgz")),
        destination = target.value,
        chartUpdate = _.copy(version = "3.2.3+meta.data"),
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
        },
      )
    ),
  )

assertGeneratedValues := {
  val tempChartValues = target.value / s"$cn-0" / cn / "values.yaml"
  yaml.parser.parse(new FileReader(tempChartValues)) match {
    case Right(json) =>
      val cursor = json.hcursor
      val expected: Set[String] = Set("replicaCount", "long", "dict")
      val all: Set[String] = cursor.keys.get.toSet
      if(!expected.forall(all.contains)) throw new AssertionError(s"Test fail, values expected to contain: ${expected}, but: ${all}")
      val r = for {
        image <- cursor.get[Json]("image")
        replicaCount <- cursor.get[Int]("replicaCount")
        repository <- image.hcursor.get[String]("repository")
        tag <- image.hcursor.get[String]("tag")
      } yield repository == "nginx2" && tag == "someTag" && replicaCount == 6
      assert(r.getOrElse(false), "Test fail, wrong values.yaml settings detected")
    case Left(err: Throwable) => throw err
  }
}
