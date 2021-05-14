import java.io.FileReader
import java.net.URI

import _root_.io.circe.{Json, yaml}
import _root_.io.github.shelm.ChartLocation.Local
import _root_.io.github.shelm.ChartLocation
import _root_.io.github.shelm.HelmPlugin.autoImport.Helm
import _root_.io.github.shelm.ChartPackagingSettings
import _root_.io.github.shelm.ChartRepositorySettings

val cn = "salt"
lazy val assertGeneratedValues = taskKey[Unit]("Assert packageValueOverrides")

lazy val root = (project in file("."))
  .enablePlugins(HelmPlugin)
  .settings(
    version := "0.1",
    scalaVersion := "2.13.4",
    Helm / chartSettings := Seq(
      ChartPackagingSettings(
        chartLocation = ChartLocation.RemoteRepository(cn, URI.create("https://kiemlicz.github.io/ambassador/"), ChartRepositorySettings.NoAuth, Some("2.1.3")),
        destination = target.value,
        fatalLint = false,
        chartUpdate = c => c.copy(version=s"${c.version}+extraMetaData2"),
        valueOverrides = _ => Seq(
          Json.fromFields(
            Iterable(
              "nameOverride" -> Json.fromString("testNameSalt"),
            )
          )
        ),
        includeFiles = Seq(
          file("includeme") -> "extrainclude"
        )
      )
    )
  )

assertGeneratedValues := {
  val tempChartValues = target.value / s"$cn-0" / cn  / "values.yaml"
  yaml.parser.parse(new FileReader(tempChartValues)) match {
    case Right(json) =>
      assert(json.hcursor.get[String]("nameOverride").getOrElse("") == "testNameSalt", "Expected namOverride equal to: testNameSalt")
    case Left(err: Throwable) => throw err
  }
}