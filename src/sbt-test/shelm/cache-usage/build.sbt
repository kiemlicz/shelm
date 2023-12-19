import _root_.io.circe.{Json, yaml}
import _root_.io.github.kiemlicz.shelm.HelmPlugin.autoImport.Helm
import _root_.io.github.kiemlicz.shelm._

import java.net.URI

val cn = "salt"

lazy val root = (project in file("."))
  .enablePlugins(HelmPlugin)
  .settings(
    version := "0.1",
    scalaVersion := "2.13.4",
    Helm / chartSettings := Seq(
      ChartSettings(
        chartLocation = ChartLocation.RemoteRepository(
          ChartName(cn), URI.create("https://kiemlicz.github.io/ambassador/"), ChartRepositoryAuth.NoAuth, Some("2.1.3")
        )
      )
    ),
  )

