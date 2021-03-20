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

//todo multiple artifacts, some duplicated
//fixme version swap
lazy val root = (project in file("."))
  .enablePlugins(HelmPlugin, HelmPublishPlugin)
  .settings(
    version := "0.1",
    credentials += Credentials("Artifactory Realm Helm", "repository.avsystem.com", "user", "password"),
//    resolvers += "Artifactory Realm Helm" at "https://repository.avsystem.com/artifactory/helm-local/experiment/" ,
    Helm / publishTo := Some(Resolver.url("Artifactory Helm", url("https://repository.avsystem.com/artifactory/helm/experiment/"))(Patterns("[artifact]-[revision].[ext]"))),
    Helm / repositories := Seq(
      ChartRepository(ChartRepositoryName("ambassador"), URI.create("https://kiemlicz.github.io/ambassador/"))
    ),
    Helm / shouldUpdateRepositories := true,
    Helm / chartSettings := Seq(
      ChartPackagingSettings(
        chartLocation = ChartLocation.AddedRepository(cn, ChartRepositoryName("ambassador"), Some("2.1.3")),
        destination = target.value,
        fatalLint = false,
        valueOverrides = _ => Seq(
          Json.fromFields(
            Iterable(
              "nameOverride" -> Json.fromString("testNameSalt"),
            )
          )
        ),
      )
    )
  )

assertGeneratedValues := {
  val tempChartValues = target.value / cn  / "values.yaml"
  yaml.parser.parse(new FileReader(tempChartValues)) match {
    case Right(json) =>
      assert(json.hcursor.get[String]("nameOverride").getOrElse("") == "testNameSalt", "Expected namOverride equal to: testNameSalt")
    case Left(err: Throwable) => throw err
  }
}
