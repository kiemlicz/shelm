import com.shelm.{ChartLocation, ChartMaintainer, ChartPackagingSettings, ChartRepositorySettings}

import java.net.URI

lazy val root = (project in file("."))
  .enablePlugins(HelmPublishPlugin)
  .settings(
    version := "0.1",
    scalaVersion := "2.13.3",
    publishTo := {
      val tpe = if (version.value.contains("SNAPSHOT")) "snapshot" else "release"
      Some(tpe at s"https://repository.example.com/")
    },
    publishMavenStyle := true,
    publish / skip := true,
    Helm / publish / skip := false,
    Helm / publishTo := Some(
      Resolver.url(
        "REPO Realm",
        url(s"https://repository.example.com/artifactory/helm/kk/")
      )(Patterns("[chartMajor].[chartMinor].[chartPatch]/[chartName]-[chartVersion].[ext]"))
    ),
    Helm / chartSettings := Seq(
      ChartPackagingSettings(
        chartLocation = ChartLocation.RemoteRepository(
          "keycloak",
          URI.create("https://codecentric.github.io/helm-charts"),
          ChartRepositorySettings.NoAuth,
          Some("8.3.0")
        ),
        destination = target.value / "kk",
        chartUpdate = chart => chart.copy(
          version = version.value,
          appVersion = Some(version.value),
          name = "kk",
          maintainers = Some(List(new ChartMaintainer(
            name = "some",
            email = Some("some@example.com"),
            url = None
          ))),
          sources = Some(new URI("https://example/com") :: chart.sources.toList.flatten)
        ),
      )
    ),
  )
