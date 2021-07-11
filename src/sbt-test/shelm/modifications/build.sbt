import _root_.io.github.kiemlicz.shelm.HelmPlugin.autoImport.Helm
import _root_.io.github.kiemlicz.shelm._

lazy val root = (project in file("."))
  .enablePlugins(HelmPlugin)
  .settings(
    version := "0.1",
    scalaVersion := "2.13.3",
    Helm / chartSettings := Seq(
      ChartPackagingSettings(
        chartLocation = ChartLocation.Local(file("modifications-chart")),
        destination = target.value,
        chartUpdate = _.copy(version = "2.2.3+meta.data", description = Some("added description"))
      )
    ),
    Helm / chartMappings := { s =>
      ChartMappings(
        s,
        includeFiles = Seq(
          file("config") -> "config"
        ),
        yamlsToMerge = Seq(
          file("values.yaml") -> "values.yaml"
        )
      )
    }
  )
