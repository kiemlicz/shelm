# Simple Helm Plugin - SHelm
Create Helm Chart for your application with configuration files.

Helm long-standing [issue](https://github.com/helm/helm/issues/3276) about addition external files to Helm Charts.  
This plugin is mainly about addressing this issue. 
It allows users to add any additional files to the Helm Chart  
The plugin doesn't impose security issues raised in aforementioned ticket, the additional files are accessible only during build time.

## Requirements 
Helm3 binary is required.

## Usage
|SBT command | description |
|-|-|
|`helm:create`|lints and creates Helm Chart|
|`helm:lint`|lints Helm Chart|
|`helm:prepare`|copies Chart directory into `target/chartName` directory with all configured dependencies|

# Releasing SHelm
Uses SemVer2 with GitVersioning: https://github.com/rallyhealth/sbt-git-versioning

[Consult following README](https://github.com/rallyhealth/sbt-git-versioning#notes) regarding versioning 

Release is performed from [Github action](https://github.com/kiemlicz/shelm/actions?query=workflow%3ARelease), using:
https://github.com/rallyhealth/sbt-git-versioning#recommended--drelease  
Git tag is published **after** the successful release.
