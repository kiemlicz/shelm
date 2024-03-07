import _root_.io.circe.{Json, yaml}
import _root_.io.github.kiemlicz.shelm.HelmPlugin.autoImport.Helm
import _root_.io.github.kiemlicz.shelm._

import java.io.FileReader
import java.net.URI

val cn = "cilium"
lazy val assertPublish = taskKey[Unit]("Assert publish to Chart Museum")

lazy val root = (project in file("."))
  .enablePlugins(HelmPlugin, HelmPublishPlugin)
  .settings(
    version := "0.1",
    Helm / shouldUpdateRepositories := true,
    Helm / publishHelmToIvyRepo := false,
    Helm / repositories := Seq(
      IvyCompatibleHttpChartRepository(ChartRepositoryName("stable"), URI.create("https://charts.helm.sh/stable")),
      IvyCompatibleHttpChartRepository(ChartRepositoryName("cilium"), URI.create("https://helm.cilium.io/")),
    ),
    Helm / publishToHosting := Seq(
//      ChartMuseumRepository(ChartRepositoryName("bla"), URI.create("http://localhost:8081/api/charts"), ChartRepositoryAuth.NoAuth),
      ChartMuseumRepository(ChartRepositoryName("bla"), URI.create("http://localhost:8081/api/charts"), ChartRepositoryAuth.UserPassword("test", "test")),
//      ChartMuseumRepository(ChartRepositoryName("bla"), URI.create("http://localhost:8082/api/charts"), ChartRepositoryAuth.Bearer("")),
    ),
//    Helm / isSnapshot := true,
    Helm / chartSettings := Seq(
      ChartSettings(
        chartLocation = ChartLocation.AddedRepository(ChartName(cn), ChartRepositoryName("cilium"), Some("1.9.5")),
        metadata = Some(SimpleChartMetadata("a"))
      ),
      ChartSettings(
        chartLocation = ChartLocation.AddedRepository(ChartName(cn), ChartRepositoryName("cilium"), Some("1.9.5")),
        metadata = Some(SimpleChartMetadata("b")))
    ),
    Helm / chartMappings := {
      case s@ChartSettings(ChartLocation.AddedRepository(ChartName(`cn`), ChartRepositoryName("cilium"), Some("1.9.5")), Some(SimpleChartMetadata("a"))) =>
        ChartMappings(
          s,
          destination = target.value,
          chartUpdate = c => c.copy(
            version = "2.1.3+meta1",
          ),
          Seq.empty, Seq.empty, valueOverrides = _ => Seq(
            Json.fromFields(
              Iterable(
                "nameOverride" -> Json.fromString("testNameSalt1"),
              )
            )
          ),
          lintSettings = LintSettings(fatalLint=false)
        )
      case s@ChartSettings(ChartLocation.AddedRepository(ChartName(`cn`), ChartRepositoryName("cilium"), Some("1.9.5")), Some(SimpleChartMetadata("b"))) =>
        ChartMappings(
          s,
          destination = target.value,
          chartUpdate = c => c.copy(
            version = "2.1.3+meta2",
          ),
          Seq.empty, Seq.empty, valueOverrides = _ => Seq(
            Json.fromFields(
              Iterable(
                "nameOverride" -> Json.fromString("testNameSalt2"),
              )
            )
          ),
          lintSettings = LintSettings(fatalLint=false)
        )
      case _ => throw new RuntimeException("unexpected")
    },

  )

assertPublish := {
  (Helm / publish).value
}
