import sbt.Keys.{pomIncludeRepository, publishMavenStyle, publishTo}

lazy val root = (project in file("."))
  .enablePlugins(SbtPlugin, SemVerPlugin)
  .settings(
    name := "shelm",
    homepage := Some(url("https://github.com/kiemlicz/shelm")),
    organization := "io.github.kiemlicz",
    organizationHomepage := Some(url("https://github.com/kiemlicz")),
    description := "Simple Helm plugin for creating Helm Charts",
    licenses += "The MIT License" -> url("https://opensource.org/licenses/MIT"),
    developers := List(
      Developer(
        id = "kiemlicz",
        name = "Stanley",
        email = "stanislaw.dev@gmail.com",
        url = url("https://github.com/kiemlicz")
      )
    ),
    javacOptions ++= Seq("-source", "17"),
    //don't specify scalaVersion for plugins (sbt is built for Scala 2.12 only)
    scalacOptions ++= Seq(
      "-encoding", "utf8",
      "-deprecation",
      "-unchecked",
      "-Xlint",
      "-feature",
      "-language:postfixOps",
      "-Xfatal-warnings",
      "-Xsource:3"
    ),
    resolvers += "Sonatype releases".at("https://oss.sonatype.org/content/repositories/releases/"),
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-yaml" % "0.14.2",
      "io.circe" %% "circe-parser" % "0.14.2",
      "org.apache.commons" % "commons-compress" % "1.27.1",
      "commons-io" % "commons-io" % "2.18.0",
      "org.scalatest" %% "scalatest" % "3.2.10" % "test"
    ),
    scriptedLaunchOpts := scriptedLaunchOpts.value ++ Seq("-Xmx1024M", "-Dplugin.version=" + version.value),
    scriptedBufferLog := true,
    scriptedBatchExecution := true,
    scriptedParallelInstances := java.lang.Runtime.getRuntime.availableProcessors(),
    //    useCoursier:=false, //https://youtrack.jetbrains.com/issue/SCL-17825
  )
  .settings(mavenCentralSettings())

def mavenCentralSettings(): Seq[Def.Setting[_]] = {
  val shelmRepoUrl = "https://github.com/kiemlicz/shelm"
  val sonatypeHost = "s01.oss.sonatype.org"
  Seq(
    credentials += {
      for {
        username <- sys.env.get("MVN_USERNAME")
        token <- sys.env.get("MVN_TOKEN")
      } yield Credentials(
        "Sonatype Nexus Repository Manager",
        sonatypeHost,
        username,
        token
      )
    },
    pgpSigningKey := sys.env.get("PGP_KEY_ID"),
    publishTo := sonatypePublishTo.value,
    sonatypeCredentialHost := sonatypeHost,
    pomIncludeRepository := (_ => false),
    publishMavenStyle := true,
    scmInfo := Some(ScmInfo(url(shelmRepoUrl), s"scm:https://github.com/kiemlicz/${name.value}.git"))
  )
}

def githubSettings(): Seq[Def.Setting[_]] = {
  //Dedicated access token must be provided for every user of this package
  val ghRepoUrl: String = s"https://maven.pkg.github.com/kiemlicz/shelm"
  val ghRepo: MavenRepository = "GitHub Package Registry".at(ghRepoUrl)
  Seq(
    credentials += sys.env
      .get("GITHUB_TOKEN")
      .map(token =>
        Credentials(
          "GitHub Package Registry",
          "maven.pkg.github.com",
          "_",
          token,
        )
      ),
    publishTo := Some(ghRepo),
    pomIncludeRepository := (_ => false),
    publishMavenStyle := true,
    resolvers ++= Seq(ghRepo),
    scmInfo := Some(ScmInfo(url(ghRepoUrl), s"scm:git@github.com:kiemlicz/${name.value}.git"))
  )
}
