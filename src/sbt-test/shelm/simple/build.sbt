import _root_.io.github.kiemlicz.shelm.ChartLocation.Local
import _root_.io.github.kiemlicz.shelm.ChartLocation
import _root_.io.github.kiemlicz.shelm.HelmPlugin.autoImport.Helm
import _root_.io.github.kiemlicz.shelm.ChartPackagingSettings

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
