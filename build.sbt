name := "shelm"
organization := "com.kiemlicz"
version := "0.1-SNAPSHOT"
//don't specify scalaVersion for plugins

libraryDependencies ++= Seq(
  "io.circe" %% "circe-yaml" % "0.13.1",
)

publishMavenStyle := false

scriptedLaunchOpts := { scriptedLaunchOpts.value ++
  Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
}
scriptedBufferLog := false

enablePlugins(SbtPlugin)
