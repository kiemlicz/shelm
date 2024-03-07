import _root_.io.circe.{Json, yaml}
import _root_.io.github.kiemlicz.shelm.HelmPlugin.autoImport.Helm
import _root_.io.github.kiemlicz.shelm._

import java.io.FileReader
import java.net.URI

val cn = "salt"
lazy val assertGeneratedValues = taskKey[Unit]("Assert packageValueOverrides")

lazy val root = (project in file("."))
  .enablePlugins(HelmPlugin)
  .settings(
    version := "0.1",
    scalaVersion := "2.13.4",
    Helm / repositories := Seq(
      IvyCompatibleHttpChartRepository(ChartRepositoryName("ambassador"), URI.create("https://kiemlicz.github.io/ambassador/"))
    ),
    Helm / shouldUpdateRepositories := true,
    Helm / chartSettings := Seq(
      ChartSettings(
        chartLocation = ChartLocation.AddedRepository(ChartName(cn), ChartRepositoryName("ambassador"), Some("2.1.3"))
      )
    ),
    Helm / chartMappings := { s =>
      ChartMappings(
        s,
        destination = target.value,
        Predef.identity,
        Seq.empty,
        Seq.empty,
        valueOverrides = _ => Seq(
          Json.fromFields(
            Iterable(
              "nameOverride" -> Json.fromString("testNameSalt"),
            )
          )
        ),
        dependencyUpdate = true,
        lintSettings = LintSettings(fatalLint=false)
      )
    }
  )

assertGeneratedValues := {
  val tempChartValues = target.value / s"$cn-0" / cn / "values.yaml"
  yaml.parser.parse(new FileReader(tempChartValues)) match {
    case Right(json) =>
      assert(
        json.hcursor.get[String]("nameOverride").getOrElse("") == "testNameSalt",
        "Expected namOverride equal to: testNameSalt"
      )
    case Left(err: Throwable) => throw err
  }
}
