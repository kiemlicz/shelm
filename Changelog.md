# Next

# 0.8.0

1. `addRepositories` renamed to `setupRegistries` and return type changed to `Unit`
   Rationale: `helm repo` is incompatible with `helm registry` command, yet semantically they "feel" like they should do
   the same
2. `ChartRepository` renamed to `LegacyHttpChartRepository`
3. `ChartRepositorySettings` renamed to `ChartRepositoryAuth`
4. 