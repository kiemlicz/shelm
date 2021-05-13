lazy val root = (project in file("."))
  .enablePlugins(SbtPlugin, SemVerPlugin)
  .settings(
    name := "shelm",
    organization := "com.kiemlicz",
    description := "Simple Helm plugin for creating Helm Charts",
    licenses += "The MIT License" -> url("https://opensource.org/licenses/MIT"),
    //don't specify scalaVersion for plugins
    scalacOptions ++= Seq(
      "-encoding", "utf8",
      "-deprecation",
      "-unchecked",
      "-Xlint",
      "-feature",
      "-language:postfixOps",
      "-Xfatal-warnings"
    ),
    resolvers += "Sonatype releases".at("https://oss.sonatype.org/content/repositories/releases/"),
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-yaml" % "0.13.1",
      "org.apache.commons" % "commons-compress" % "1.20",
      "commons-io" % "commons-io" % "2.7",
    ),
    scriptedLaunchOpts := scriptedLaunchOpts.value ++ Seq("-Xmx1024M", "-Dplugin.version=" + version.value),
    scriptedBufferLog := true,
    scriptedBatchExecution := true,
    scriptedParallelInstances := java.lang.Runtime.getRuntime.availableProcessors()
  )
  .settings(jitpackSettings())

def githubSettings(): Def.Setting[_] = {
  //unused due to requirement of setting auth in order to download public packages...
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

def jitpackSettings(): Seq[Def.Setting[_]] = {
  val jitPackRepoUrl = "https://jitpack.io"
  val jitpackRepo = "jitpack".at(jitPackRepoUrl)
  Seq(
    publishTo := Some(jitpackRepo),
    resolvers ++= Seq(jitpackRepo),
    publishMavenStyle := true,
    scmInfo := Some(ScmInfo(url(jitPackRepoUrl), s"scm:git@github.com:kiemlicz/${name.value}.git"))
  )
}
