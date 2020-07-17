lazy val root = (project in file("."))
  .settings(
    version := "0.1",
    scalaVersion := "2.13.3",
    chartDirectory in Helm := file("modifications-chart"),
    chartYaml in Helm := {
      val orig = (chartYaml in Helm).value
      val mod = orig.copy(description = Some("added description"))
      mod
    },
    chartVersion in Helm := "2.2.3+meta.data",
    packageIncludeFiles in Helm := Seq(
      file("config") -> "config",
    ),
    packageMergeYamls in Helm := Seq(
      file("values.yaml") -> "values.yaml"
    )
  )
  .enablePlugins(HelmPlugin)
//check these:
//https://github.com/rallyhealth/sbt-git-versioning/blob/master/src/sbt-test/semver/release/build.sbt