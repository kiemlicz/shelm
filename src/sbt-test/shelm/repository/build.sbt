import java.io.FileReader

import _root_.io.circe.{Json, yaml}
import com.shelm.ChartLocation.Local
import com.shelm.ChartLocation
import com.shelm.HelmPlugin.autoImport.Helm
import com.shelm.ChartPackagingSettings

val cn = "prometheus-operator"
lazy val assertGeneratedValues = taskKey[Unit]("Assert packageValueOverrides")

lazy val root = (project in file("."))
  .enablePlugins(HelmPlugin)
  .settings(
    version := "0.1",
    scalaVersion := "2.13.3",
    target in Helm := target.value / "nestTarget",
    Helm / chartSettings := Seq(
      ChartPackagingSettings(
        chartLocation = ChartLocation.Repository("stable", cn, Some("9.3.1")),
        destination = target.value / "someExtraDir",
        fatalLint = false, //due to Helm 3.3 strict naming validation
        chartUpdate = c => c.copy(version=s"${c.version}+extraMetaData"),
        valueOverrides = _ => Seq(
          Json.fromFields(
            Iterable(
              "nameOverride" -> Json.fromString("testNameProm"),
            )
          )
        ),
        includeFiles = Seq(
          file("extraConfig") -> "extraConfig"
        )
      )
    )
  )

assertGeneratedValues := {
  val tempChartValues = target.value / "nestTarget" / cn  / "values.yaml"
  yaml.parser.parse(new FileReader(tempChartValues)) match {
    case Right(json) =>
      assert(json.hcursor.get[String]("nameOverride").getOrElse("") == "testNameProm", "Expected namOverride equal to: testNameProm")
    case Left(err: Throwable) => throw err
  }
}