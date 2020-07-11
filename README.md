# Simple Helm Plugin - SHelm
Generates Helm Chart with given additional directories  

Helm long-standing [issue](https://github.com/helm/helm/issues/3276) about addition external files to Helm Charts
This plugin mainly addresses this issue, allows users to add any files that will be put to chart during the build time  
The plugin doesn't impose security issues raised in aforementioned ticket

## Requirements 
Helm3 binary is required.

## Usage
|SBT command | description |
|-|-|
|`helm:createPackage`|lints and creates Helm Chart|

## Releasing SHelm
Uses SemVer2 with GitVersioning: https://github.com/rallyhealth/sbt-git-versioning

[Consult following README](https://github.com/rallyhealth/sbt-git-versioning#notes) 

Release is performed from Github action, using:
https://github.com/rallyhealth/sbt-git-versioning#recommended--drelease