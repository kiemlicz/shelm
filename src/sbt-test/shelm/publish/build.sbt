import _root_.io.circe.{Json, yaml}
import _root_.io.github.kiemlicz.shelm.HelmPlugin.autoImport.Helm
import _root_.io.github.kiemlicz.shelm._

import java.io.FileReader
import java.net.URI

val cn = "cilium"
lazy val assertGeneratedValues = taskKey[Unit]("Assert packageValueOverrides")

lazy val root = (project in file("."))
  .enablePlugins(HelmPlugin, HelmPublishPlugin)
  .settings(
    version := "0.1",
    resolvers += Resolver.file("local", file("./repo/"))(
      Patterns("[chartMajor].[chartMinor].[chartPatch]/[artifact]-[chartVersion].[ext]")
    ),
    // for helm:publishLocal the `local` resolver must be set,
    //    Helm / publishTo := Some(Resolver.file("local", file("/tmp/repo/"))(Patterns("[chartMajor].[chartMinor].[chartPatch]/[artifact]-[chartVersion].[ext]"))),
    Helm / shouldUpdateRepositories := true,
    Helm / repositories := Seq(
      ChartRepository(ChartRepositoryName("stable"), URI.create("https://charts.helm.sh/stable")),
      ChartRepository(ChartRepositoryName("cilium"), URI.create("https://helm.cilium.io/")),
    ),
    Helm / chartSettings := Seq(
      ChartPackagingSettings(
        chartLocation = ChartLocation.AddedRepository(cn, ChartRepositoryName("cilium"), Some("1.9.5")),
        destination = target.value,
        chartUpdate = c => c.copy(
          version = "2.1.3+meta1",
        ),
        fatalLint = false,
        metadata = Some("a")
      ),
      ChartPackagingSettings(
        chartLocation = ChartLocation.AddedRepository(cn, ChartRepositoryName("cilium"), Some("1.9.5")),
        destination = target.value,
        chartUpdate = c => c.copy(
          version = "2.1.3+meta2",
        ),
        fatalLint = false,
        metadata = Some("b")
      )
    ),
    Helm / chartMappings := {
      case s@ChartPackagingSettings(ChartLocation.AddedRepository(`cn`, ChartRepositoryName("cilium"), Some("1.9.5")), _, _, _, _, Some("a")) =>
        ChartMappings(
          s, Seq.empty, Seq.empty, valueOverrides = _ => Seq(
            Json.fromFields(
              Iterable(
                "nameOverride" -> Json.fromString("testNameSalt1"),
              )
            )
          )
        )
      case s@ChartPackagingSettings(ChartLocation.AddedRepository(`cn`, ChartRepositoryName("cilium"), Some("1.9.5")), _, _, _, _, Some("b")) =>
        ChartMappings(
          s, Seq.empty, Seq.empty, valueOverrides = _ => Seq(
            Json.fromFields(
              Iterable(
                "nameOverride" -> Json.fromString("testNameSalt2"),
              )
            )
          )
        )
      case _ => throw new RuntimeException("unexpected")
    }
  )

assertGeneratedValues := {
  yaml.parser.parse(new FileReader(target.value / s"$cn-0" / cn / "values.yaml")) match {
    case Right(json) =>
      assert(
        json.hcursor.get[String]("nameOverride").getOrElse("") == "testNameSalt1",
        "Expected namOverride equal to: testNameSalt1"
      )
    case Left(err: Throwable) => throw err
  }
  yaml.parser.parse(new FileReader(target.value / s"$cn-1" / cn / "values.yaml")) match {
    case Right(json) =>
      assert(
        json.hcursor.get[String]("nameOverride").getOrElse("") == "testNameSalt2",
        "Expected namOverride equal to: testNameSalt2"
      )
    case Left(err: Throwable) => throw err
  }
}
