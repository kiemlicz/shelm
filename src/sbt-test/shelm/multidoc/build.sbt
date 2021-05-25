import _root_.io.github.kiemlicz.shelm.ChartLocation.Local
import _root_.io.github.kiemlicz.shelm.ChartLocation
import _root_.io.github.kiemlicz.shelm.HelmPlugin.autoImport.Helm
import _root_.io.github.kiemlicz.shelm.ChartPackagingSettings
import _root_.io.circe.{Json, yaml}
import java.io.FileReader

lazy val assertGeneratedValues = taskKey[Unit]("Assert packageValueOverrides")
val cn = "multidoc-chart"

lazy val root = (project in file("."))
  .enablePlugins(HelmPlugin)
  .settings(
    version := "0.1",
    scalaVersion := "2.13.3",
    Helm / chartSettings := Seq(
      ChartPackagingSettings(
        chartLocation = ChartLocation.Local(file(cn)),
        destination = target.value,
        chartUpdate = _.copy(version = "2.2.3+meta.data", description = Some("added description")),
        includeFiles = Seq(
          file("config") -> "config"
        ),
        yamlsToMerge = Seq(
          file("values.yaml") -> "values.yaml"
        ),
      )
    ),
  )

assertGeneratedValues := {
  val tempChartValues = target.value / s"$cn-0" / cn / "values.yaml"
  yaml.parser.parse(new FileReader(tempChartValues)) match {
    case Right(json) =>
      val cursor = json.hcursor
      val expected: Set[String] = Set("replicaCount", "securityContext")
      val all: Set[String] = cursor.keys.get.toSet
      if(!expected.forall(all.contains)) throw new AssertionError(s"Test fail, values expected to contain: ${expected}, but: ${all}")
      val r = for {
        image <- cursor.get[Json]("image")
        securityContext <- cursor.get[Json]("securityContext")
        replicaCount <- cursor.get[Int]("replicaCount")
        repository <- image.hcursor.get[String]("repository")
        tag <- image.hcursor.get[String]("tag")
        fsGroup <- securityContext.hcursor.get[Int]("fsGroup")
      } yield repository == "nginx" && tag == "1.2.3" && replicaCount == 3 && fsGroup == 1000
      assert(r.getOrElse(false), "Test fail, wrong values.yaml settings detected")
    case Left(err: Throwable) => throw err
  }
}