import _root_.io.circe.{Json, yaml}
import _root_.io.github.kiemlicz.shelm.HelmPlugin.autoImport.Helm
import _root_.io.github.kiemlicz.shelm.*

import java.io.FileReader
import java.net.URI

val cn = "cilium"
lazy val assertPublish = taskKey[Unit]("Assert publish to OCI registry")

lazy val root = (project in file("."))
  .enablePlugins(HelmPlugin, HelmPublishPlugin)
  .settings(
    version := "0.1",
    Helm / shouldUpdateRepositories := true,
    Helm / repositories := Seq(
      IvyCompatibleHttpChartRepository(ChartRepositoryName("stable"), URI.create("https://charts.helm.sh/stable")),
      IvyCompatibleHttpChartRepository(ChartRepositoryName("cilium"), URI.create("https://helm.cilium.io/")),
//      OciChartRegistry(URI.create("oci://registry-1.docker.io/kiemlicz/"), ChartRepositoryAuth.Bearer("XXX", Some("kiemlicz"))),
      OciChartRegistry(URI.create("oci://localhost:5011/test/"), ChartRepositoryAuth.UserPassword("test", "test")),
    ),
    Helm / publishTo := Some(Resolver.file("local", file("/tmp/repo/"))(Patterns("[chartMajor].[chartMinor].[chartPatch]/[artifact]-[chartVersion].[ext]"))),
    resolvers += Resolver.file("local", file("./repo/"))(
      Patterns("[chartMajor].[chartMinor].[chartPatch]/[artifact]-[chartVersion].[ext]")
    ),
    Helm / publishRegistries := (Helm / repositories).value,
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
          fatalLint = false,
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
          fatalLint = false
        )
      case _ => throw new RuntimeException("unexpected")
    }
  )

assertPublish := {
  (Helm / publish).value
 // throw new IllegalStateException() //to dump logs ...
}
