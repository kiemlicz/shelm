name := "shelm"
organization := "com.kiemlicz"
description := "Simple Helm plugin for creating Helm Charts"
licenses += "The MIT License" -> url("https://opensource.org/licenses/MIT")

//don't specify scalaVersion for plugins

libraryDependencies ++= Seq(
  "io.circe" %% "circe-yaml" % "0.13.1",
)

publishMavenStyle := false
bintrayRepository := "sbt-plugins"
bintrayOrganization in bintray := None

scriptedLaunchOpts := { scriptedLaunchOpts.value ++
  Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
}
scriptedBufferLog := false

enablePlugins(SbtPlugin)
enablePlugins(SemVerPlugin)