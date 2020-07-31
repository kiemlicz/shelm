name := "shelm"
organization := "com.kiemlicz"
description := "Simple Helm plugin for creating Helm Charts"
licenses += "The MIT License" -> url("https://opensource.org/licenses/MIT")

//don't specify scalaVersion for plugins
scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature", "-Xfatal-warnings")

val circeVersion = "0.13.0"
libraryDependencies ++= Seq(
  "io.circe" %% "circe-yaml" % circeVersion,
  "io.circe" %% "circe-generic" % circeVersion
)

scriptedLaunchOpts := {
  scriptedLaunchOpts.value ++
    Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
}
scriptedBufferLog := false

def githubSettings(): Def.Setting[_] = {
  //disabled due to requirement of setting auth in order to download public packages...
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
  resolvers ++= Seq(ghRepo)
  scmInfo := Some(ScmInfo(url(ghRepoUrl), s"scm:git@github.com:kiemlicz/${name.value}.git"))
}

def bintraySettings(): Def.Setting[_] = {
  publishMavenStyle := false
  bintrayOrganization in bintray := None
  bintrayRepository := "sbt-plugins"
}
bintraySettings()

enablePlugins(SbtPlugin)
enablePlugins(SemVerPlugin)
