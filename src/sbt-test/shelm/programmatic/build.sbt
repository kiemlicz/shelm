import java.io.FileReader

import _root_.io.circe.{Json, yaml}

lazy val assertGeneratedValues = taskKey[Unit]("Assert packageValueOverrides")
val cn = "programmatic-chart"

lazy val root = (project in file("."))
  .settings(
    version := "0.1",
    scalaVersion := "2.13.3",
    chartDirectory in Helm := file(cn),
    chartVersion in Helm := "4.2.3+meta.data",
    chartAppVersion in Helm := Some("1.2"),
    packageValueOverrides in Helm := Seq(Json.fromFields(
      Iterable(
        "long" -> Json.fromLong(1),
        "dict" -> Json.fromFields(Iterable("nest" -> Json.fromString("programmatic"))),
        "service" -> Json.fromFields(Iterable(
          "port" -> Json.fromInt(123),
          "type" -> Json.fromString("ClusterIP")
        )),
        "ingress" -> Json.fromFields(Iterable(
          "enabled" -> Json.fromBoolean(false)
        ))
      )
    ))
  )
  .enablePlugins(HelmPlugin)

assertGeneratedValues := {
  val tempChartValues = target.value / cn / "values.yaml"
  yaml.parser.parse(new FileReader(tempChartValues)) match {
    case Right(json) =>
      val cursor = json.hcursor
      val expected: Set[String] = Set("long", "dict", "service", "ingress")
      val got: Set[String] = cursor.keys.get.toSet
      if(!got.equals(expected)) throw new AssertionError(s"Test fail, expected: ${expected}, but: ${got}")
    case Left(err: Throwable) => throw err
  }
}
