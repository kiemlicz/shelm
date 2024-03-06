import _root_.io.circe.{Json, yaml}
import _root_.io.github.kiemlicz.shelm.HelmPlugin.autoImport.Helm
import _root_.io.github.kiemlicz.shelm._

import java.io.FileReader
import java.net.URI

val cn = "shelm-test"
lazy val assertGeneratedValues = taskKey[Unit]("Assert packageValueOverrides")

lazy val root = (project in file("."))
  .enablePlugins(HelmPlugin)
  .settings(
    version := "0.1",
    scalaVersion := "2.13.4",
    Helm / chartSettings := Seq(
      ChartSettings(
        chartLocation = ChartLocation.RemoteOciRegistry( //fixme move to local OCI registry spawned with docker compose
          ChartName(cn), URI.create(s"oci://registry-1.docker.io/kiemlicz/${cn}"), Some("0.1.0")
        )
      )
    ),
    Helm / chartMappings := {
      s: ChartSettings =>
        ChartMappings(
          s,
          destination = target.value,
          chartUpdate = c => c.copy(version = s"${c.version}+some.1"),
          includeFiles = Seq(
            file("includeme") -> "extrainclude"
          ),
          yamlsToMerge = Seq.empty,
          valueOverrides = _ => Seq(
            Json.fromFields(
              Iterable(
                "nameOverride" -> Json.fromString("shelm-test-2"),
              )
            )
          ),
          fatalLint = false
        )
    }
  )


assertGeneratedValues := {
  val tempChartValues = target.value / s"$cn-0" / cn / "values.yaml"
  yaml.parser.parse(new FileReader(tempChartValues)) match {
    case Right(json) =>
      assert(
        json.hcursor.get[Int]("replicaCount").getOrElse(0) == 1,
        "Expected replicaCount equal to: 1"
      )
    case Left(err: Throwable) => throw err
  }
}