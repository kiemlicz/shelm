lazy val root = (project in file("."))
  .settings(
    version := "0.1",
    scalaVersion := "2.13.3",
    chartDirectory in Helm := file("includes-chart"),
    chartVersion in Helm := "1.2.3+meta.data",
    packageIncludeFiles in Helm := Seq(
      file("config") -> file("config"),
      file("secrets") -> file("secrets"),
      file("config2/single.conf") -> file("config/single.conf"),
    ),
    packageMergeYamls in Helm := Seq(
      file("values.yaml") -> file("values.yaml")
    )
  )
//check these:
//https://github.com/rallyhealth/sbt-git-versioning/blob/master/src/sbt-test/semver/release/build.sbt