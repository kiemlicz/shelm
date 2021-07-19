import _root_.io.circe.{Json, yaml}
import _root_.io.github.kiemlicz.shelm.HelmPlugin.autoImport.Helm
import _root_.io.github.kiemlicz.shelm._

import java.io.FileReader

val cn = "redis"
lazy val assertGeneratedValues = taskKey[Unit]("Assert packageValueOverrides")

lazy val root = (project in file("."))
  .enablePlugins(HelmPlugin)
  .settings(
    version := "0.1",
    scalaVersion := "2.13.3",
    target in Helm := target.value / "nestTarget",
    Helm / chartSettings := Seq(
      ChartSettings(
        chartLocation = ChartLocation.AddedRepository(ChartName(cn), ChartRepositoryName("stable"), Some("10.5.7")),
      )
    ),
    Helm / chartMappings := { s =>
      ChartMappings(
        s,
        destination = target.value / "someExtraDir",
        chartUpdate = c => c.copy(version = s"${c.version}+extraMetaData"),
        includeFiles = Seq(
          file("extraConfig") -> "extraConfig"
        ),
        Seq.empty,
        valueOverrides = _ => Seq(
          Json.fromFields(
            Iterable(
              "nameOverride" -> Json.fromString("testNameRedis"),
            )
          )
        ),
        fatalLint = false, //due to Helm 3.3 strict naming validation
      )
    }
  )

assertGeneratedValues := {
  val tempChartValues = target.value / "nestTarget" / s"$cn-0" / cn / "values.yaml"
  yaml.parser.parse(new FileReader(tempChartValues)) match {
    case Right(json) =>
      assert(
        json.hcursor.get[String]("nameOverride").getOrElse("") == "testNameRedis",
        "Expected namOverride equal to: testNameRedis"
      )
    case Left(err: Throwable) => throw err
  }
}