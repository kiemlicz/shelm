lazy val root = (project in file("."))
  .settings(
    version := "0.1",
    scalaVersion := "2.13.3",
    chartDirectory in Helm := file("simple-chart"),
    chartSetAppVersion in Helm := false,
  )
  .enablePlugins(HelmPlugin)