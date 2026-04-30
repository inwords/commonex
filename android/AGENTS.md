# Agent Instructions for Expenses (CommonEx) Android Project

For non-trivial work and when to search upstream docs, follow root [AGENTS.md](../AGENTS.md) (workflow lifecycle and freshness policy).
Cross-project reference docs: [../docs/domain.md](../docs/domain.md) and [../docs/network-contracts.md](../docs/network-contracts.md).
Android/KMM operational docs are indexed in [docs/README.md](docs/README.md).

## Project Overview (Reference)

**Kotlin Multiplatform Mobile (KMM)** expenses app targeting Android and iOS. ~50 modules with feature-based organization.

**Non-standard technologies:** Ktor + Cronet (Android) / Darwin (iOS) networking, Room KMP database, custom DI locator (`shared:core:locator`), Navigation 3, Wire protobuf for settings serialization, WorkManager for background sync.

**Freshness note:** Navigation 3, Room KMP, and Compose APIs evolve rapidly and may be newer than training data. Always verify API usage against current upstream docs when implementing. For KMP/Android test-helper sharing recommendations (especially `testFixtures`), verify current AGP and Kotlin plugin docs for the repo versions in use before proposing a pattern.

## Standard Operating Procedures and Skills (Workflow)

- Release workflow: use the `prepare-mobile-release` skill at `android/.agents/skills/prepare-mobile-release` for version bump (Android + iOS), baseline profiles, and tagging.
- Long local Android runs: use the `run-android-local-long-task` skill at `android/.agents/skills/run-android-local-long-task`; confirm environment with [`android/docs/local-agent-prerequisites.md`](docs/local-agent-prerequisites.md).

## Tooling Docs (Reference)

- Start with `android/docs/README.md` for the Android/KMM doc map.
- Most-used canonical docs:
    - `android/docs/mobile-sync-and-sharing.md` - Offline-first IDs, join/share links, deep links, and sync behavior.
    - `android/docs/android-runtime-operations.md` - Android startup/runtime behavior, Sentry, WorkManager, sync bootstrap, and AppFunctions.
    - `android/docs/local-agent-prerequisites.md` - Local Android and iOS prerequisites, version sources of truth, and CI-vs-local expectations.
- iOS release and validation docs:
    - `android/docs/ios-validation-checklist.md`
    - `android/docs/ios-app-privacy.md`
    - `android/docs/ios-versioning.md`
- Tool-specific docs:
    - `android/docs/jetbrains-mcp.md`
    - `android/marathon/README.md`
    - `android/gradle/README.md`
- Benchmark docs:
    - `android/docs/database-benchmarking.md`
    - `android/docs/database-research-log.md`
    - `android/docs/database-research-log-template.md`

## Build Instructions (Workflow)

### Prerequisites

- JDK: same as CI (see `.github/workflows/android.yml`); project JVM target 17. Use wrapper for Gradle (version in `gradle/wrapper/gradle-wrapper.properties`).
- Android SDK: API level from `app/build.gradle.kts`. See [`docs/local-agent-prerequisites.md`](docs/local-agent-prerequisites.md) for details.

### Essential Commands

**ALWAYS** use `--quiet` flag when running Gradle tasks. Exit code 0 of `gradlew` invocation is **ALWAYS** success regardless of output. Run from `android/` directory.

On Windows use `.\gradlew` and `;` instead of `&&`; quote `-D` properties.

```bash
# Clean and build
./gradlew --quiet clean
./gradlew --quiet assembleDebug
./gradlew --quiet assembleRelease

# Unit tests
./gradlew --quiet test
./gradlew --quiet allTests                     # all targets including KMM
./gradlew --quiet testHostTest                 # KMM host tests only (~15s)

# Focused module compile/test
./gradlew --quiet :shared:core:network:compileAndroidMain
./gradlew --quiet :shared:core:network:compileIosMainKotlinMetadata
./gradlew --quiet :shared:core:network:testAndroidHostTest --tests "com.inwords.expenses.core.network.RequestRetryTest"
# Note: module host-test tasks use :<module>:testAndroidHostTest (not :<module>:testHostTest)

# Instrumented tests (requires device/emulator)
./gradlew --quiet :app:connectedAutotestAndroidTest
./gradlew --quiet :app:connectedAutotestAndroidTest "-Dcom.android.tools.r8.disableApiModeling=true" "-Pandroid.testInstrumentationRunnerArguments.class=ru.commonex.BasicInstrumentedTest#testName"
./gradlew --quiet connectedAndroidDeviceTest   # includes Room tests

# Managed devices (no booted emulator needed)
./gradlew --quiet :app:pixel6Api35AtdAutotestAndroidTest "-Dcom.android.tools.r8.disableApiModeling=true"
./gradlew --quiet :app:pixel6Api35AtdAndroidDeviceTest

# Code coverage (Kover)
./gradlew --quiet testHostTest :app:test -Pkover koverHtmlReport koverXmlReport

# Lint
./gradlew --quiet lint --continue
./gradlew --quiet lintDebug
./gradlew --quiet lintFix

# Dependency updates (5+ minutes, do not interrupt)
./gradlew --quiet dependencyUpdates --refresh-dependencies -Drevision=release
```

For build warnings and platform-specific notes, see [`docs/troubleshooting.md`](docs/troubleshooting.md).

## Architecture (Reference)

Modular KMM project: `shared/core/` (infrastructure), `shared/feature/` (features), `shared/integration/` (platform glue), `app/` (Android), `iosApp/` (iOS). See [`docs/project-structure.md`](docs/project-structure.md) for full module tree, key files, and configuration.

## Development Guidelines (Reference)

- Changes in `shared/` affect both Android and iOS.
- Room DAOs are auto-generated by KSP; do not edit generated files.
- Avoid duplicating code: prefer delegating to existing domain interactors instead of reimplementing in integration layers.
- Required persistence state: do not use nullable Room entity fields for required persisted state; initialize in fresh-create and migration paths.
- Room data updates: prefer targeted DAO `UPDATE` queries over read-then-upsert.
- Explicit minimal-change requests: if the user asks to minimize changes in Compose pane/ViewModel files, avoid incidental helper extraction, preview churn, or broad refactors unless required for correctness.

### Component Factory Deps Pattern

- For KMP `*ComponentFactory` APIs, if `expect`/`actual` `Deps` repeat the same shared members, extract into `*ComponentFactoryCommonDeps` in `commonMain`.
- Keep only platform-specific members in platform `actual interface Deps`.
- Do not introduce `*CommonDeps` when the common contract is empty.

### Coding Patterns

See [`docs/patterns.md`](docs/patterns.md) for ViewModel, Compose UI, state modeling, form input, and architecture/wiring patterns.

### Compose Material 3 UI/UX Rules (Strict)

For any Compose UI change, follow the 40 mandatory rules in [`docs/compose-ui-rules.md`](docs/compose-ui-rules.md). All items are required and blocking.

### UI Development

- Follow Material 3 Expressive guidelines and design system from `shared:core:ui-design`
- Platform-specific implementations in `androidMain`/`iosMain` source sets
- Navigation: use Navigation 3 library with helpers from `shared:core:navigation`

### Testing

Unit tests: JUnit 6. Instrumented UI E2E: JUnit 4 + Marathon. Prefer Marathon for local UI-test validation. See [`docs/testing-patterns.md`](docs/testing-patterns.md) for full testing strategy, instrumented test architecture, and KMM test patterns.

### Feature Workflows

For adding features, entities, currencies, database migrations, and event sharing details, see [`docs/feature-workflows.md`](docs/feature-workflows.md).

## Validation Steps (Workflow)

### MCP-First Validation Policy

- Use JetBrains MCP tools as the default validation path when MCP is available for the open Android project.
- Prefer `execute_terminal_command` (MCP terminal) for compile/build/test validation after edits.
- For Android UI-test validation, prefer the MCP terminal and the repo script `./scripts/run-marathon.ps1`.
- Prefer `get_file_problems` for per-file diagnostics on edited files.
- If MCP is unavailable, run equivalent Gradle/CLI validation and note the fallback reason.

### Before Submitting Changes

```bash
./gradlew --quiet assembleDebug
./gradlew --quiet test
./gradlew --quiet testHostTest
./gradlew --quiet lint --continue
# macOS only: verify iOS targets compile
./gradlew --quiet :shared:integration:base:linkDebugFrameworkIosSimulatorArm64
# Windows/Linux: skip iOS link step, report host limitation
./gradlew --quiet assembleRelease
./gradlew --quiet assembleAutotest
# Optional: Marathon for Compose UI / E2E validation
# ./scripts/run-marathon.ps1
```

### Quick Validation (small changes)

```bash
./gradlew --quiet testHostTest
./gradlew --quiet lintDebug
./gradlew --quiet :shared:feature:sync:testAndroidHostTest --tests "com.inwords.expenses.feature.sync.domain.EventsSyncObserverTest"
./gradlew --quiet :shared:core:network:testAndroidHostTest --tests "com.inwords.expenses.core.network.RequestRetryTest"
```

### Comprehensive Testing (before major releases)

```bash
./gradlew --quiet allTests
./gradlew --quiet allDevicesCheck
./gradlew --quiet lint --continue
```

Treat `allTests` as a full local gate only on hosts with iOS prerequisites (see `android/docs/local-agent-prerequisites.md`).

Prefer these instructions for repo conventions, but verify against current upstream docs when implementing with APIs you're not fully confident about. If upstream docs conflict with this file, flag the conflict.
