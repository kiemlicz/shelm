name := "shelm"

version := "0.1"
organization := "kiemlicz"

//don't specify scalaVersion for plugins
//scalaVersion := "2.13.3"

libraryDependencies ++= Seq(
  "com.avsystem.commons" %% "commons-core" % "1.46.0",
  "io.circe" %% "circe-yaml" % "0.13.1",
)

enablePlugins(SbtPlugin)
