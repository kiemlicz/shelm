import java.io.FileReader

import _root_.io.circe.{Json, yaml}

lazy val assertGeneratedValues = taskKey[Unit]("Assert packageValueOverrides")
val cn = "overrides-chart"

lazy val root = (project in file("."))
  .settings(
    version := "0.1",
    scalaVersion := "2.13.3",
    chartDirectory in Helm := file(cn),
    chartVersion in Helm := "3.2.3+meta.data",
    chartAppVersion in Helm := Some("1.1"),
    packageIncludeFiles in Helm := Seq(
      file("config") -> "config",
    ),
    packageMergeYamls in Helm := Seq(
      file("values-override.yaml") -> "values.yaml"
    ),
    packageValueOverrides in Helm := { case Some(values) => Seq(
        Json.fromFields(Iterable(
          "replicaCount" -> Json.fromInt(values.hcursor.get[Int]("replicaCount").map(_ + 4).getOrElse(throw new IllegalStateException("Test fail: no replicaCount field found"))),
          "long" -> Json.fromLong(450),
          "dict" -> Json.fromFields(Iterable(
            "nest" -> Json.fromString("overrides")
          ))
        ))
      )
    case _ => throw new IllegalStateException("test fail: no values.yaml found, they are required for this test")
    }
  )
  .enablePlugins(HelmPlugin)

assertGeneratedValues := {
  val tempChartValues = target.value / cn / "values.yaml"
  yaml.parser.parse(new FileReader(tempChartValues)) match {
    case Right(json) =>
      val cursor = json.hcursor
      val expected: Set[String] = Set("replicaCount", "long", "dict")
      val all: Set[String] = cursor.keys.get.toSet
      if(!expected.forall(all.contains)) throw new AssertionError(s"Test fail, values expected to contain: ${expected}, but: ${all}")
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
