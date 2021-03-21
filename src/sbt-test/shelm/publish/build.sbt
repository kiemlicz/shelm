import _root_.io.circe.{Json, yaml}

import java.net.URI
import com.shelm.ChartLocation.Local
import com.shelm.ChartLocation
import com.shelm.HelmPlugin.autoImport.Helm
import com.shelm.HelmPublishPlugin
import com.shelm.ChartPackagingSettings
import com.shelm.ChartRepository
import com.shelm.ChartRepositoryName

import java.io.FileReader

val cn = "salt"
lazy val assertGeneratedValues = taskKey[Unit]("Assert packageValueOverrides")

lazy val root = (project in file("."))
  .enablePlugins(HelmPlugin, HelmPublishPlugin)
  .settings(
    version := "0.1",
    resolvers += Resolver.file("local", file("./repo/"))(Patterns("[chartMajor].[chartMinor].[chartPatch]/[artifact]-[chartVersion].[ext]")),
    // for helm:publishLocal the `local` resolver must be set,
//    Helm / publishTo := Some(Resolver.file("local", file("/tmp/repo/"))(Patterns("[chartMajor].[chartMinor].[chartPatch]/[artifact]-[chartVersion].[ext]"))),
    Helm / shouldUpdateRepositories := true,
    Helm / repositories := Seq(
      ChartRepository(ChartRepositoryName("stable"), URI.create("https://charts.helm.sh/stable")),
      ChartRepository(ChartRepositoryName("ambassador"), URI.create("https://kiemlicz.github.io/ambassador/")),
    ),
    Helm / chartSettings := Seq(
      ChartPackagingSettings(
        chartLocation = ChartLocation.AddedRepository(cn, ChartRepositoryName("ambassador"), Some("2.1.3")),
        destination = target.value,
        chartUpdate = c => c.copy(
          version = "2.1.3+meta1",
        ),
        fatalLint = false,
        valueOverrides = _ => Seq(
          Json.fromFields(
            Iterable(
              "nameOverride" -> Json.fromString("testNameSalt1"),
            )
          )
        ),
      ),
      ChartPackagingSettings(
        chartLocation = ChartLocation.AddedRepository(cn, ChartRepositoryName("ambassador"), Some("2.1.3")),
        destination = target.value,
        chartUpdate = c => c.copy(
          version = "2.1.3+meta2",
        ),
        fatalLint = false,
        valueOverrides = _ => Seq(
          Json.fromFields(
            Iterable(
              "nameOverride" -> Json.fromString("testNameSalt2"),
            )
          )
        ),
      )
    )
  )

assertGeneratedValues := {
  yaml.parser.parse(new FileReader(target.value / s"$cn-0" / cn  / "values.yaml")) match {
    case Right(json) =>
      assert(json.hcursor.get[String]("nameOverride").getOrElse("") == "testNameSalt1", "Expected namOverride equal to: testNameSalt1")
    case Left(err: Throwable) => throw err
  }
  yaml.parser.parse(new FileReader(target.value / s"$cn-1" / cn  / "values.yaml")) match {
    case Right(json) =>
      assert(json.hcursor.get[String]("nameOverride").getOrElse("") == "testNameSalt2", "Expected namOverride equal to: testNameSalt2")
    case Left(err: Throwable) => throw err
  }
}
