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
|`helm:create`|lints and creates Helm Chart|
|`helm:lint`|lints Helm Chart|
|`helm:prepare`|copies Chart directory into `target/chartName` directory with all configured dependencies|

## Example
Refer to [tests](https://github.com/kiemlicz/shelm/tree/master/src/sbt-test/shelm) for complete examples

_project/plugins.sbt_
```
addSbtPlugin("com.kiemlicz" % "shelm" % "0.0.8")
```
_build.sbt_
```
lazy val root = (project in file("."))
  .settings(
    version := "0.1",
    scalaVersion := "2.13.3",
    chartDirectory in Helm := file("directory-with-helm-chart"),
    chartVersion in Helm := "1.2.3+meta.data",
    packageIncludeFiles in Helm := Seq(
      file("config") -> "config",
      file("secrets") -> "secrets",
      file("config2/single.conf") -> "config/single.conf",
    ),
    packageMergeYamls in Helm := Seq(
      file("values.yaml") -> "values.yaml"
    ),
    packageValueOverrides in Helm := Seq(
         Json.fromFields(Iterable(
         "replicaCount" -> Json.fromInt(4),
         "someKey" -> Json.fromLong(42),
         "image" -> Json.fromFields(Iterable(
           "repository" -> Json.fromString("some.repo/image")
           "tag" -> Json.fromString("latest")
         ))
       ))
    )
  )
  .enablePlugins(HelmPlugin)
```

# Releasing SHelm
Uses SemVer2 with GitVersioning: https://github.com/rallyhealth/sbt-git-versioning

[Consult following README](https://github.com/rallyhealth/sbt-git-versioning#notes) regarding versioning 

Release is performed from [Github action](https://github.com/kiemlicz/shelm/actions?query=workflow%3ARelease), using:
https://github.com/rallyhealth/sbt-git-versioning#recommended--drelease  
Git tag is published **after** the successful release.
