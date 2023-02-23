import _root_.io.circe.{Json, yaml}
import _root_.io.github.kiemlicz.shelm.HelmPlugin.autoImport.Helm
import _root_.io.github.kiemlicz.shelm._

lazy val assertArtifacts = taskKey[Unit]("Consume packagedArtifacts")
lazy val assertValuesYaml = taskKey[Unit]("Check if values.yaml is a proper yaml file")

import java.io.FileReader

val cn = "simple-chart"

lazy val root = (project in file("."))
  .enablePlugins(HelmPlugin, HelmPublishPlugin)
  .settings(
    version := "0.1.0",
    scalaVersion := "2.13.3",
    Helm / chartSettings := Seq(
      ChartSettings(
        chartLocation = ChartLocation.Local(file(cn))

      )
    ),
    Helm / chartMappings := { s =>
      ChartMappings(
        s, target.value,
        _.copy(version = "0.3.0-rc.1+some-meta-info-2021.01.01-even.more.124", description = Some("added description")),
        yamlsToMerge = Seq(file("empty.yaml") -> "values.yaml")
      ) // bug reproduction
    }
  )

assertValuesYaml := {
  // bug reproduction empty values or non-existing values.yaml when packaged twice turned into yaml containing one line:
  // `false`
  val chartValues = target.value / s"$cn-0" / cn / "values.yaml"
  yaml.parser.parse(new FileReader(chartValues)) match {
    case Right(json) =>
      val cursor = json.hcursor
      assert(cursor.keys.isEmpty)
    case Left(err: Throwable) => throw err
  }
  assert(IO.read(chartValues) == "", "expecting empty values file") // comments are not retained
}

assertArtifacts := {
  val a = (Helm / artifacts).value
  assert(a.size == 1, s"Expected exactly one setting artifact")
  val pkgArtifacts = (Helm / packagedArtifacts).value
  assert(pkgArtifacts.size == 1, s"Expected exactly one packagedArtifact")
  val version = pkgArtifacts.map(res => (
    res._1.extraAttributes.getOrElse("chartMajor", throw new RuntimeException("No major version")),
    res._1.extraAttributes.getOrElse("chartMinor", throw new RuntimeException("No minor version")),
    res._1.extraAttributes.getOrElse("chartPatch", throw new RuntimeException("No patch version"))
  )
  ).toList
  assert(version.length == 1, "Expected exactly one artifact")
  assert(version.head._1 == "0", "Expected major version == 0")
  assert(version.head._2 == "3", "Expected major version == 3")
  assert(version.head._3 == "0", "Expected major version == 0")
}
