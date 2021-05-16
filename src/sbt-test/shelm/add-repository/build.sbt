import _root_.io.circe.{Json, yaml}
import java.net.URI
import _root_.io.github.kiemlicz.shelm.ChartLocation.Local
import _root_.io.github.kiemlicz.shelm.ChartLocation
import _root_.io.github.kiemlicz.shelm.HelmPlugin.autoImport.Helm
import _root_.io.github.kiemlicz.shelm.ChartPackagingSettings
import _root_.io.github.kiemlicz.shelm.ChartRepository
import _root_.io.github.kiemlicz.shelm.ChartRepositoryName
import java.io.FileReader

val cn = "salt"
lazy val assertGeneratedValues = taskKey[Unit]("Assert packageValueOverrides")

lazy val root = (project in file("."))
  .enablePlugins(HelmPlugin)
  .settings(
    version := "0.1",
    scalaVersion := "2.13.4",
    Helm / repositories := Seq(
      ChartRepository(ChartRepositoryName("ambassador"), URI.create("https://kiemlicz.github.io/ambassador/"))
    ),
    Helm / shouldUpdateRepositories := true,
    Helm / chartSettings := Seq(
      ChartPackagingSettings(
        chartLocation = ChartLocation.AddedRepository(cn, ChartRepositoryName("ambassador"), Some("2.1.3")),
        destination = target.value,
        fatalLint = false,
        valueOverrides = _ => Seq(
          Json.fromFields(
            Iterable(
              "nameOverride" -> Json.fromString("testNameSalt"),
            )
          )
        ),
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
