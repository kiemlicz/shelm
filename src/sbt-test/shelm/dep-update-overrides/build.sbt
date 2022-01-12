import _root_.io.circe.{Json, yaml}
import _root_.io.github.kiemlicz.shelm.HelmPlugin.autoImport.Helm
import _root_.io.github.kiemlicz.shelm._
import org.apache.commons.io.FileUtils

import java.io.{FileInputStream, FileReader}
import java.net.URI

lazy val assertGeneratedValues = taskKey[Unit]("Assert packageValueOverrides")
lazy val assertOverride = taskKey[Unit]("Assert overridden files")
val cn = "kube-prometheus-stack"
val cnVer = "27.2.0"
val toOverride = "charts/kube-state-metrics/templates/servicemonitor.yaml"

lazy val root = (project in file("."))
  .enablePlugins(HelmPlugin)
  .settings(
    version := "0.1",
    scalaVersion := "2.13.3",
    Helm / chartSettings := Seq(
      ChartSettings(
        chartLocation = ChartLocation
          .RemoteRepository(
            ChartName("kube-prometheus-stack"),
            URI.create("https://prometheus-community.github.io/helm-charts"),
            ChartRepositorySettings.NoAuth,
            Some(cnVer)
          ),
      )
    ),
    Helm / chartMappings := { s =>
      ChartMappings(
        s,
        destination = target.value,
        chartUpdate = u => u.copy(version = s"${u.version}+extra"),
        includeFiles = Seq(
          file("config") / "kube-state-servicemonitor.yaml" -> toOverride
        ),
        yamlsToMerge = Seq(
          file("values-override.yaml") -> "values.yaml"
        )
      )
    }
  )

assertGeneratedValues := {
  val tempChartValues = target.value / s"$cn-0" / cn / "values.yaml"
  yaml.parser.parse(new FileReader(tempChartValues)) match {
    case Right(json) =>
      val cursor = json.hcursor
      val r = for {
        ksm <- cursor.get[Json]("kubeStateMetrics")
        extraFlag <- ksm.hcursor.get[String]("extraFlag")
      } yield extraFlag == "something"
      assert(r.getOrElse(false), "Test fail, wrong values.yaml settings detected")
    case Left(err: Throwable) => throw err
  }
}

assertOverride := {
  val overriddenYaml = target.value / s"$cn-0" / cn / toOverride
  val packagedChart = target.value / s"$cn-$cnVer+extra.tgz"
  val unpacked = target.value / s"toAssert"
  val fileToAssert = target.value / s"toAssert" / cn / toOverride
  val found = IO.readLines(overriddenYaml).filter(_.contains("caFile: /etc/prom-certs/root-cert.pem"))
  assert(found.length == 2, "Expected 'caFile: /etc/prom-certs/root-cert.pem' two times")

  ChartDownloader.open(new FileInputStream(packagedChart))
    .getOrElse(throw new IllegalStateException(s"Unable to open Helm Chart: $packagedChart"))
    .foreach {
      case (entry, is) =>
        try {
          val archiveEntry = unpacked / entry.getName
          IO.write(archiveEntry, IO.readBytes(is))
        } finally {
          is.close()
        }
    }
  assert(FileUtils.contentEquals(file("config") / "kube-state-servicemonitor.yaml", fileToAssert), "config/kube-state-servicemonitor.yaml and packaged version differs")
}
