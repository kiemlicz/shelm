import _root_.io.github.kiemlicz.shelm._

lazy val assertArtifacts = taskKey[Unit]("Consume packagedArtifacts")

lazy val root = (project in file("."))
  .enablePlugins(HelmPlugin, HelmPublishPlugin)
  .settings(
    version := "0.1.0",
    scalaVersion := "2.13.3",
    Helm / chartSettings := Seq(
      ChartSettings(
        chartLocation = ChartLocation.Local(file("redis"))
      )
    ),
    Helm / chartMappings := { s =>
      ChartMappings(
        settings = s,
        destination = target.value,
        chartUpdate = _.copy(description = Some("added description")),
        includeFiles = Seq(
          file("config") -> "config"
        ),
        yamlsToMerge = Seq(
          file("redis-values.yaml") -> "values.yaml"
        ),
      )
    }
  )

assertArtifacts := {
  val a = (Helm / artifacts).value
  assert(a.size == 1, s"Expected exactly one setting artifact")
  val pkgArtifacts = (Helm / packagedArtifacts).value
  assert(pkgArtifacts.size == 1, s"Expected exactly one packagedArtifact")
}
