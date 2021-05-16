import java.io.FileReader
import _root_.io.github.kiemlicz.shelm.ChartLocation.Local
import _root_.io.github.kiemlicz.shelm.ChartLocation
import _root_.io.github.kiemlicz.shelm.HelmPlugin.autoImport.Helm
import _root_.io.github.kiemlicz.shelm.ChartPackagingSettings
import _root_.io.circe.{Json, yaml}

lazy val assertGeneratedValues = taskKey[Unit]("Assert packageValueOverrides")
val cn = "programmatic-chart"

lazy val root = (project in file("."))
  .enablePlugins(HelmPlugin)
  .settings(
    version := "0.1",
    scalaVersion := "2.13.3",
    Helm / chartSettings := Seq(
      ChartPackagingSettings(
        chartLocation = ChartLocation.Local(file(cn)),
        destination = target.value,
        chartUpdate = _.copy(version = "4.2.3+meta.data", appVersion = Some("1.2")),
        valueOverrides = { _ =>
          Seq(
            Json.fromFields(
              Iterable(
                "long" -> Json.fromLong(1),
                "dict" -> Json.fromFields(Iterable("nest" -> Json.fromString("programmatic"))),
                "service" -> Json.fromFields(
                  Iterable(
                    "port" -> Json.fromInt(123),
                    "type" -> Json.fromString("ClusterIP"),
                  )
                ),
                "ingress" -> Json.fromFields(
                  Iterable(
                    "enabled" -> Json.fromBoolean(false)
                  )
                ),
              )
            )
          )
        },
      )
    ),
  )

assertGeneratedValues := {
  val tempChartValues = target.value / s"$cn-0" / cn / "values.yaml"
  yaml.parser.parse(new FileReader(tempChartValues)) match {
    case Right(json) =>
      val cursor = json.hcursor
      val expected: Set[String] = Set("long", "dict", "service", "ingress")
      val got: Set[String] = cursor.keys.get.toSet
      assert(got.equals(expected), s"Test fail, expected: $expected, but: $got")
    case Left(err: Throwable) => throw err
  }
}
