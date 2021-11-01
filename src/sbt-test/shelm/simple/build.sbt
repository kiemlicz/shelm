import _root_.io.github.kiemlicz.shelm._

lazy val assertArtifacts = taskKey[Unit]("Consume packagedArtifacts")

lazy val root = (project in file("."))
  .enablePlugins(HelmPlugin, HelmPublishPlugin)
  .settings(
    version := "0.1.0",
    scalaVersion := "2.13.3",
    Helm / chartSettings := Seq(
      ChartSettings(
        chartLocation = ChartLocation.Local(file("simple-chart"))

      )
    ),
    Helm / chartMappings := { s =>
      ChartMappings(s, target.value, _.copy(version = "0.3.0-rc.1+some-meta-info-2021.01.01-even.more.124", description = Some("added description")))
    }
  )

assertArtifacts := {
  val a = (Helm / artifacts).value
  assert(a.size == 1, s"Expected exactly one setting artifact")
  val pkgArtifacts = (Helm / packagedArtifacts).value
  assert(pkgArtifacts.size == 1, s"Expected exactly one packagedArtifact")
  val version = pkgArtifacts.map(res => (
    res._1.extraAttributes.getOrElse("chartMajor", throw new RuntimeException("No major version")),
    res._1.extraAttributes.getOrElse("chartMinor", throw new RuntimeException("No minor version")),
    res._1.extraAttributes.getOrElse("chartPatch", throw new RuntimeException("No patch version"))
  )).toList
  assert(version.length == 1, "Expected exactly one artifact")
  assert(version.head._1 == "0", "Expected major version == 0")
  assert(version.head._2 == "3", "Expected major version == 3")
  assert(version.head._3 == "0", "Expected major version == 0")
}
