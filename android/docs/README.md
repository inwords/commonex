# Android/KMM Docs

Use this index as the Android and iOS operational doc map. Keep detailed guidance in the focused docs below; keep `android/AGENTS.md` concise and operational.

## Start Here

- [`../AGENTS.md`](../AGENTS.md) - Android/KMM build, validation, workflow, and agent rules.
- [`../../docs/domain.md`](../../docs/domain.md) - Cross-project product and domain reference.
- [`../../docs/network-contracts.md`](../../docs/network-contracts.md) - Cross-project HTTP and transport contracts.

## Core Mobile Behavior

- [`mobile-sync-and-sharing.md`](mobile-sync-and-sharing.md) - Canonical mobile reference for offline-first IDs, join/share links, deep links, and sync behavior.
- [`android-runtime-operations.md`](android-runtime-operations.md) - Android startup/runtime behavior, Sentry, StrictMode, WorkManager, sync bootstrap, and AppFunctions.
- [`patterns.md`](patterns.md) - Android/KMM coding and architecture patterns.

## Local Setup And Tooling

- [`local-agent-prerequisites.md`](local-agent-prerequisites.md) - Local Android and iOS prerequisites, version sources of truth, and CI-vs-local expectations.
- [`jetbrains-mcp.md`](jetbrains-mcp.md) - JetBrains MCP scope, verification flow, and fallback guidance.
- [`../marathon/README.md`](../marathon/README.md) - Local Marathon runner setup and usage.
- [`../gradle/README.md`](../gradle/README.md) - Gradle profiler, dependency-updates, and baseline-profile tooling.

## iOS Sync

- [`ios-background-sync.md`](ios-background-sync.md) - Planned approach for reliable iOS background sync (beginBackgroundTask + BGTaskScheduler).

## iOS Release And Validation

- [`ios-validation-checklist.md`](ios-validation-checklist.md) - Pre-submission simulator, archive, device, and privacy-report validation.
- [`ios-app-privacy.md`](ios-app-privacy.md) - Privacy-manifest and App Store Connect questionnaire guidance.
- [`ios-versioning.md`](ios-versioning.md) - iOS version/build policy and Android alignment.

## Benchmarking And Research

- [`database-benchmarking.md`](database-benchmarking.md) - Canonical Room benchmark workflow and correctness policy.
- [`database-research-log.md`](database-research-log.md) - Current benchmark conclusions and artifact references.
- [`database-research-log-template.md`](database-research-log-template.md) - Template for new database benchmark entries.
