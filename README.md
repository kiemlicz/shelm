# Simple Helm Plugin - SHelm
This plugin enables packaging and publishing Helm Charts from SBT. It offers support for OCI registries and enables the validation of Charts during the build process.

1. **Build-Time Chart Management**    
    Fully manage Chart during build-time, enables adding "external" configuration files.  
    Helm has long-standing [issue](https://github.com/helm/helm/issues/3276) about addition of external files into Helm Charts.  
    This plugin "kind of" addresses this issue.  
    It allows users to add any additional files to the Helm Chart without imposing security issues raised in the aforementioned ticket.
    The additional files are accessible only during build time and packaged into Chart.

2. **Chart Publishing**    
    SHelm supports the publishing of Charts to Helm repositories.  
    It is compatible with OCI-based registries, Chart Museum (utilizing an dedicated HTTP client, eliminating the need for `cm-plugin`), and 'ivy-style' repositories such as Artifactory.

3. **Chart Linting**    
    Starting from SHelm 0.8.0, users can utilize a strict linter and [lint against a provided Kubernetes version](https://github.com/helm/helm/pull/10677).  
    This feature enhances the development process by identifying potential issues early on.

## Usage
See [Changelog](Changelog.md) for compatibility and upgrade instructions.

Description of main introduced tasks

### HelmPlugin
| command                     | description                                                                                                                                                                                                                                                                                      |
|-----------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `Helm / packagesBin`        | lints and creates Helm Chart                                                                                                                                                                                                                                                                     |
| `Helm / lint`               | lints Helm Chart. Strict lint requires Helm version >= 3.14                                                                                                                                                                                                                                      |
| `Helm / prepare`            | copies Chart directory into `target/chartName` directory with all configured dependencies                                                                                                                                                                                                        |
| `Helm / setupRegistries`    | login to OCI registries and adds Helm repositories configured with `Helm / repositories` setting. Adding existing repository multiple times is considered a safe operation. However, the `https://repo/stable` and `https://repo/stable/` are different URLs and cannot be added under same name |
| `Helm / updateRepositories` | performs `helm repo update`                                                                                                                                                                                                                                                                      |
| `Helm / publish`            | Publishes the Chart into configured repository                                                                                                                                                                                                                                                   |

### HelmPublishPlugin
| command                     | description                                                                        |
|-----------------------------|------------------------------------------------------------------------------------|
| `Helm / publish`            | publishes Charts into configured `Helm / publishToHosting` registries/repositories |


## Requirements 
Helm 3 [binary](https://helm.sh/docs/intro/install/) is required.  
OCI registries are supported starting 3.8.  
Linter against given Kubernetes version is supported starting with 3.14.  

## Example
Refer to [tests](https://github.com/kiemlicz/shelm/tree/master/src/sbt-test/shelm) for complete examples

Add `shelm` plugin to project:  
_project/plugins.sbt_
```
addSbtPlugin("io.github.kiemlicz" % "shelm" % "0.9.0")
```
Check [tags](https://github.com/kiemlicz/shelm/tags) for latest available version.

1\. Create Chart from the local directory.  
#### **`build.sbt`**
```
lazy val root = (project in file("."))
  .enablePlugins(HelmPlugin)
  .settings(
    version := "0.1",
    scalaVersion := "2.13.3",
    Helm / chartSettings := Seq(
      ChartSettings(
        chartLocation = ChartLocation.Local(ChartName("includes-chart"), file("includes-chart")),
      )
    ),
    Helm / chartMappings := { s =>
      ChartMappings(
        s,
        destination = target.value,
        chartUpdate = _.copy(version = "1.2.3+meta.data"),
        includeFiles = Seq(
          file("config") -> "config",
          file("secrets") -> "secrets",
          file("config2/single.conf") -> "config/single.conf",
        ),
        yamlsToMerge = Seq(
          file("values.yaml") -> "values.yaml"
        )
      )
    }
  )
```
`sbt> Helm / packagesBin` creates: `projectRoot/target/chart_name-1.2.3+meta.data.tgz`, which contains `config`, `config2` and `secrets` dirs.
Additionally, the `values.yaml` from Chart's directory will be merged with `values.yaml` present in project root.

2\. Create Chart which is in the already added Helm repository (re-pack).
#### **`build.sbt`**
```
lazy val root = (project in file("."))
  .enablePlugins(HelmPlugin)
  .settings(
    version := "0.1",
    scalaVersion := "2.13.3",
    target in Helm := target.value / "nestTarget",
    Helm / shouldUpdateRepositories := true,
    Helm / chartSettings := Seq(
      ChartSettings(
        chartLocation = ChartLocation.AddedRepository(ChartName("redis"), ChartRepositoryName("stable"), Some("10.5.7")),
      )
    ),
    Helm / chartMappings := { s =>
      ChartMappings(
        s,
        destination = target.value / "someExtraDir",
        chartUpdate = c => c.copy(version = s"${c.version}+extraMetaData"),
        includeFiles = Seq(
          file("extraConfig") -> "extraConfig"
        ),
        Seq.empty,
        valueOverrides = _ => Seq(
          Json.fromFields(
            Iterable(
              "nameOverride" -> Json.fromString("testNameRedis"),
            )
          )
        ),
        lintSettings = LintSettings(fatalLint=false)
      )
    }
  )
```
`sbt> Helm / packagesBin` creates: `projectRoot/target/someExtraDir/redis-10.5.7+extraMetaData.tgz`, 
the downloaded and unpacked Chart can be found: `projectRoot/target/nestTarget/redis`.
The re-packed Redis Chart will contain `extraConfig` and `nameOverride` key set in `values.yaml`

It is also possible to use direct URI for Chart: `ChartLocation.Remote(URI.create("https://github.com/kiemlicz/ambassador/raw/gh-pages/salt-2.1.2.tgz"))`  
or not `helm repo add`'ed repository: `ChartLocation.RemoteRepository("thename", URI.create("https://kiemlicz.github.io/ambassador/"), ChartRepositorySettings.NoAuth, Some("2.1.3"))`

3\. Publish Chart

Additionally to `Helm / chartSettings` and `Helm / chartMappings`, specify the repository.

##### OCI registry
SHelm can handle OCI registry login, or it can be left to user (`Helm/registriesLoginEnabled`)

**`build.sbt`**
```
Helm / publishToHosting := Seq(OciChartRegistry(URI.create("oci://localhost:5011/test/"), ChartRepositoryAuth.UserPassword("test", "test"))),
```
Run `Helm / publish`

##### ChartMuseum repository
Mind the URL: for ChartMuseum `api/charts` path segment is required however it is not well-defined (e.g. `api/tenant/charts`) - consult ChartMuseum owner how to set this up. 
**`build.sbt`**
```
Helm / publishToHosting := Seq(ChartMuseumRepository(ChartRepositoryName("bla"), URI.create("http://localhost:8081/api/charts"), ChartRepositoryAuth.UserPassword("test", "test"))),
```
Run `Helm / publish`

See `sbt-test/shelm/publish-cm` and `sbt-test/shelm/publish-oci` for examples how to use Chart Museum and OCI registries

##### IVY-style repository
**`build.sbt`**
```
credentials += Credentials("Artifactory Realm", "repository.example.com", "user", "pass"),
Helm / publishTo := Some(Resolver.url("Artifactory Realm", url("https://repository.example.com/artifactory/helm/experiments/"))(Patterns("[chartMajor].[chartMinor].[chartPatch]/[artifact]-[chartVersion].[ext]"))),
Helm / publishRegistries := Seq(ChartRepositoryName("Artifactory"), URI.create("https://repository.example.com/artifactory/helm/experiments/")),
```
Available extra Ivy attributes (for use in `Patterns`):
- `chartName` Chart's name (`Chart.yaml`'s `name` field)
- `chartVersion` full version of the Chart
- `chartMajor` Chart's SemVer2 Major
- `chartMinor` Chart's SemVer2 Minor
- `chartPatch` Chart's SemVer2 Patch
- [`chartMetadata`] Chart's metadata appended during execution of `packagesBin` task

# Development notes
### Releasing SHelm
Release is performed using [Github action](https://github.com/kiemlicz/shelm/actions?query=workflow%3ARelease).
Run on `git tag` push.

The SHelm is versioned using SemVer2 with [GitVersioning](https://github.com/rallyhealth/sbt-git-versioning)

Artifacts are hosted on [Maven Central](https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/io/github/kiemlicz/)

[Consult following README](https://github.com/rallyhealth/sbt-git-versioning#notes) regarding the versioning. 

## Current related issues
[SBT doesn't download sources in IntelliJ](https://youtrack.jetbrains.com/issue/SCL-17825)
