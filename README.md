# Simple Helm Plugin - SHelm
Package your application's Helm Chart and all of its dependencies with "external" configuration files added.  
Enables customizing `Chart.yaml` during the build.  
You can manage application Chart and dependent Charts from the SBT

Helm has long-standing [issue](https://github.com/helm/helm/issues/3276) about addition of external files into Helm Charts.

This plugin "kind of" addresses this issue. 
It allows users to add any additional files to the Helm Chart. 
The plugin doesn't impose security issues raised in the aforementioned ticket.
The additional files are accessible only during build time and packaged into Chart.

With `shelm` it is also possible to add Helm repositories and publish Charts to configured repositories.

## Usage
| command | description |
|-|-|
|`helm:packagesBin`|lints and creates Helm Chart|
|`helm:lint`|lints Helm Chart|
|`helm:prepare`|copies Chart directory into `target/chartName` directory with all configured dependencies|
|`helm:addRepositories`|adds Helm repositories configured with `helm:repositories` setting. Adding existing repository multiple times is considered a safe operation. However, the `https://repo/stable` and `https://repo/stable/` are different URLs and cannot be added under same name|
|`helm:updateRepositories`|performs `helm repo update`|
|`helm:publish`|Publishes the Chart into configured repository|

## Requirements 
Helm 3 [binary](https://helm.sh/docs/intro/install/) is required.

## Example
Refer to [tests](https://github.com/kiemlicz/shelm/tree/master/src/sbt-test/shelm) for complete examples

Add `shelm` plugin to project:  
_project/plugins.sbt_
```
resolvers += Resolver.bintrayIvyRepo("kiemlicz", "sbt-plugins")
addSbtPlugin("com.kiemlicz" % "shelm" % "0.1.5")
```
Check [releases page](https://github.com/kiemlicz/shelm/releases) for latest available version

1\. Create Chart from the local directory.  
#### **`build.sbt`**
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
`sbt> helm:packagesBin` creates: `projectRoot/target/chart_name-1.2.3+meta.data.tgz`, which contains `config`, `config2` and `secrets` dirs.
Additionally, the `values.yaml` from Chart's directory will be merged with `values.yaml` present in project root.

2\. Create Chart which is in the repository (re-pack).
#### **`build.sbt`**
```
lazy val root = (project in file("."))
  .enablePlugins(HelmPlugin)
  .settings(
    version := "0.1",
    scalaVersion := "2.13.3",
    target in Helm := target.value / "nestTarget",
    Helm / chartSettings := Seq(
      ChartPackagingSettings(
        chartLocation = ChartLocation.Repository("stable", "prometheus-operator", Some("9.3.1")),
        destination = target.value / "someExtraDir",        
        chartUpdate = c => c.copy(version=s"${c.version}+extraMetaData"),
        valueOverrides = _ => Seq(
          Json.fromFields(
            Iterable(
              "nameOverride" -> Json.fromString("testNameProm"),
            )
          )
        ),
        includeFiles = Seq(
          file("extraConfig") -> "extraConfig"
        )
      )
    )
  )
```
`sbt> helm:packagesBin` creates: `projectRoot/target/someExtraDir/prometheus-operator-9.3.1+extraMetaData.tgz`, 
the downloaded and unpacked Chart can be found: `projectRoot/target/nestTarget/prometheusOperator`.
The re-packed prometheus Chart will contain `extraConfig` and `nameOverride` key set in `values.yaml`

It is also possible to use direct URI for Chart: `ChartLocation.Remote(URI.create("https://github.com/kiemlicz/ambassador/raw/gh-pages/salt-2.1.2.tgz"))`

3\. Publish Chart

Additionally to `Helm / chartSettings`, specify the repository.
#### **`build.sbt`**
```
credentials += Credentials("Artifactory Realm", "repository.example.com", "user", "pass"),
Helm / publishTo := Some(Resolver.url("Artifactory Realm", url("https://repository.example.com/artifactory/helm/experiments/"))(Patterns("[chartMajor].[chartMinor].[chartPatch]/[artifact]-[chartVersion].[ext]"))),
```
Available extra Ivy attributes (for use in `Patterns`):
- `chartName` Chart's name (`Chart.yaml`'s `name` field)
- `chartVersion` full version of the Chart
- `chartMajor` Chart's SemVer2 Major
- `chartMinor` Chart's SemVer2 Minor
- `chartPatch` Chart's SemVer2 Patch

# Development notes
### Releasing SHelm
Release is performed from dedicated [Github action](https://github.com/kiemlicz/shelm/actions?query=workflow%3ARelease)

The SHelm is versioned using SemVer2 with [GitVersioning](https://github.com/rallyhealth/sbt-git-versioning)

The release procedure description can be found [here](https://github.com/rallyhealth/sbt-git-versioning#recommended--drelease)  
Git tag is published **after** the successful release.

[Consult following README](https://github.com/rallyhealth/sbt-git-versioning#notes) regarding the versioning. 
