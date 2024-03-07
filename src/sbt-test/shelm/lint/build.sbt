import _root_.io.github.kiemlicz.shelm.HelmPlugin.autoImport.Helm
import _root_.io.github.kiemlicz.shelm._

lazy val assertLint = taskKey[Unit]("Assert lint")


lazy val root = (project in file("."))
  .enablePlugins(HelmPlugin)
  .settings(
    version := "0.1",
    scalaVersion := "2.13.13",
    Helm / chartSettings := Seq(
      ChartSettings(
        chartLocation = ChartLocation.Local(ChartName("testchart-ok"), file("testchart-ok"))
      ),
      ChartSettings(
        chartLocation = ChartLocation.Local(ChartName("testchart-deprecated"), file("testchart-deprecated"))
      ),
    ),
    Helm / chartMappings := { s =>
      ChartMappings(
        s,
        destination = target.value,
        chartUpdate = _.copy(version = "1.0.0"),
        yamlsToMerge = Seq(
          file("values.yaml") -> "values.yaml"
        ),
        lintSettings = LintSettings(fatalLint=true, kubeVersion=Some(SemVer2(1,25,0)), strictLint=true)
      )
    }
  )

assertLint := {
  (Helm / packagesBin).value
//  (Helm / packagesBin).result.value match {
//    case Inc(inc: Incomplete) => println("wrong" + inc)
//    case Value(v) => println("OK")
//  }
//  throw new IllegalStateException() //to dump logs ...
}
