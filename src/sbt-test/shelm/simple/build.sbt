import _root_.io.github.shelm.ChartLocation.Local
import _root_.io.github.shelm.ChartLocation
import _root_.io.github.shelm.HelmPlugin.autoImport.Helm
import _root_.io.github.shelm.ChartPackagingSettings

lazy val root = (project in file("."))
  .enablePlugins(HelmPlugin)
  .settings(
    version := "0.1",
    scalaVersion := "2.13.3",
    Helm / chartSettings := Seq(
      ChartPackagingSettings(
        chartLocation = ChartLocation.Local(file("simple-chart")),
        destination = target.value,
      )
    )
  )
