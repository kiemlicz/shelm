# Next

# 0.8.0

1. `addRepositories` renamed to `setupRegistries` and return type changed to `Unit`
   Rationale: `helm repo` is incompatible with `helm registry` command, yet semantically they "feel" like they should do
   the same
2. `ChartRepository` renamed to `IvyCompatibleHttpChartRepository`
3. `ChartRepositorySettings` renamed to `ChartRepositoryAuth`
4. Publish behavior changed. Previously only the Ivy style repositories worked (e.g. Artifactory) configured using `Resolver` in `publishTo` setting.
   In order to support these add: `Helm / publishRegistries` (see `README.md`).
5. `ChartMappings` `fatalLint` moved to `LintSettings` field 