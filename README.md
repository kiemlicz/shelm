# Simple Helm Plugin - SHelm
Create Helm Chart for your application "external" configuration files added.

Helm long-standing [issue](https://github.com/helm/helm/issues/3276) about addition external files to Helm Charts.
  
This plugin is mainly about addressing this issue. 
It allows users to add any additional files to the Helm Chart  
The plugin doesn't impose security issues raised in the aforementioned ticket, the additional files are accessible only during build time.

## Requirements 
Helm3 binary is required.

## Usage
| command | description |
|-|-|
|`helm:packagesBin`|lints and creates Helm Chart|
|`helm:lint`|lints Helm Chart|
|`helm:prepare`|copies Chart directory into `target/chartName` directory with all configured dependencies|

## Example
Refer to [tests](https://github.com/kiemlicz/shelm/tree/master/src/sbt-test/shelm) for complete examples

_project/plugins.sbt_
```
addSbtPlugin("com.kiemlicz" % "shelm" % "0.1.0")
```
_build.sbt_
```
lazy val root = (project in file("."))
  .enablePlugins(HelmPlugin)
  .settings(
    version := "0.1",
    scalaVersion := "2.13.3",
    Helm / chartSettings := Seq(
      ChartPackagingSettings(
        chartLocation = ChartLocation.Local(file("directory-with-helm-chart")),
        destination = target.value,
        chartUpdate = _.copy(version = "1.2.3+meta.data"),
        includeFiles = Seq(
          file("config") -> "config",
          file("secrets") -> "secrets",
          file("config2/single.conf") -> "config/single.conf",
        ),
        yamlsToMerge = Seq(
          file("values.yaml") -> "values.yaml"
        ),
      )
    ),
  )
```

# Releasing SHelm
Uses SemVer2 with GitVersioning: https://github.com/rallyhealth/sbt-git-versioning

[Consult following README](https://github.com/rallyhealth/sbt-git-versioning#notes) regarding versioning 

Release is performed from [Github action](https://github.com/kiemlicz/shelm/actions?query=workflow%3ARelease), using:
https://github.com/rallyhealth/sbt-git-versioning#recommended--drelease  
Git tag is published **after** the successful release.
