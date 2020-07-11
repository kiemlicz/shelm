name := "shelm"
organization := "com.kiemlicz"
description := "Simple Helm plugin for creating Helm Charts"
licenses += "The MIT License" -> url("https://opensource.org/licenses/MIT")

//don't specify scalaVersion for plugins

libraryDependencies ++= Seq(
  "io.circe" %% "circe-yaml" % "0.13.1"
)

scriptedLaunchOpts := {
  scriptedLaunchOpts.value ++
    Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
}
scriptedBufferLog := false

val ghRepoUrl: String = s"https://maven.pkg.github.com/kiemlicz/shelm"
val ghRepo: MavenRepository = "GitHub Package Registry".at(ghRepoUrl)
credentials += sys.env
  .get("GITHUB_TOKEN")
  .map(token =>
    Credentials(
      "GitHub Package Registry",
      "maven.pkg.github.com",
      "_",
      token,
    )
  )
publishTo := Some(ghRepo)
pomIncludeRepository := (_ => false)
publishMavenStyle := true
publishConfiguration := publishConfiguration.value.withOverwrite(true) // this is temporary
resolvers ++= Seq(ghRepo)
scmInfo := Some(ScmInfo(url(ghRepoUrl), s"scm:git@github.com:kiemlicz/${name.value}.git"))

enablePlugins(SbtPlugin)
enablePlugins(SemVerPlugin)