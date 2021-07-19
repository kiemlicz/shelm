import _root_.io.github.kiemlicz.shelm._

lazy val root = (project in file("."))
  .enablePlugins(HelmPlugin)
  .settings(
    version := "0.1",
    scalaVersion := "2.13.3",
    Helm / chartSettings := Seq(
      ChartSettings(
        chartLocation = ChartLocation.Local(file("simple-chart"))
      )
    ),
    Helm / chartMappings := { s =>
      ChartMappings(s, target.value)
    }
  )
