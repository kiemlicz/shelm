lazy val root = (project in file("."))
  .settings(
    version := "0.1",
    scalaVersion := "2.13.3",
    chartDirectory in Helm := file("includes-chart"),
    packageInclude in Helm := Seq(file("config"), file("secrets"), file("config2/single.conf")),
  )
