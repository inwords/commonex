# Agent Instructions for Expenses (CommonEx) Android Project

For non-trivial work and when to search upstream docs, follow root [AGENTS.md](../AGENTS.md) (workflow lifecycle and freshness policy).
Cross-project reference docs: [../docs/domain.md](../docs/domain.md) and [../docs/network-contracts.md](../docs/network-contracts.md).
Android/KMM operational docs are indexed in [docs/README.md](docs/README.md).

## Table of Contents

- [Project Overview](#project-overview-reference)
- [Standard Operating Procedures and Skills](#standard-operating-procedures-and-skills-workflow)
- [Tooling Docs](#tooling-docs-reference)
- [Build Instructions](#build-instructions-workflow)
- [Project Architecture](#project-architecture-reference)
- [Development Guidelines](#development-guidelines-reference)
- [Common Development Tasks](#common-development-tasks-workflow)
- [Debugging and Troubleshooting](#debugging-and-troubleshooting-reference)
- [Environment Setup](#environment-setup-reference)
- [Validation Steps](#validation-steps-workflow)

## Project Overview (Reference)

This is a **Kotlin Multiplatform Mobile (KMM)** expenses management application that targets both Android and iOS platforms. The project uses **Jetpack Compose** for UI, **Ktor** for networking, **Room** for local database storage, and follows a modular
architecture with feature-based organization.

**Key Technologies:**

- Kotlin 2.3.0 with Compose compiler plugin
- Jetpack Compose with Material3 design system
- Ktor client for networking with Cronet backend (Android) and Darwin backend (iOS)
- Room database with KSP for code generation
- Kotlin Coroutines and Flow for asynchronous programming
- Dependency Injection via custom locator pattern
- Navigation using Android Navigation 3 library
- Multiplatform-resources for resource management
- WorkManager for background sync tasks
- Protocol Buffers (Wire) for settings serialization
- Gradle (version in `gradle/wrapper/gradle-wrapper.properties`) with Kotlin DSL and version catalogs
- Android Gradle Plugin 9.0.0-rc03
- Android compile/target/min SDK: see `app/build.gradle.kts`

**Project Size:** ~50 modules across shared core libraries, feature modules, and platform-specific implementations.

**App Features:**

- Event creation and deletion
- Person management within events (create initially / add to existing events)
- Joining events via invite links
- Expense recording with currency conversion
- Debt calculation and split management
- Sync functionality with background workers
- Deep linking support (commonex.ru domain)

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

**Always use `.\gradlew` (Windows) for all operations. On MacOS use `./gradlew`.**

Run commands from the `android/` directory unless a command explicitly says otherwise.

#### Clean and Build

```powershell
# Clean project (7 seconds)
.\gradlew clean

# Build debug APK (28 seconds from clean, 5 seconds incremental)
.\gradlew assembleDebug

# Build release APK (longer, includes obfuscation)
.\gradlew assembleRelease
```

#### Testing

```powershell
# Run all unit tests (25 seconds)
.\gradlew test

# Run all tests across all targets with aggregated report (includes KMM)
.\gradlew allTests

# Run KMM host tests (~15 seconds)
.\gradlew testHostTest

# Compile a focused Android source set for a KMP shared module
# Note: shared modules typically expose :<module>:compileAndroidMain, not :<module>:compileKotlinAndroid
.\gradlew :shared:core:network:compileAndroidMain

# Compile iOS metadata for a focused KMP shared-module check
.\gradlew :shared:core:network:compileIosMainKotlinMetadata

# Run network retry host tests directly (module-level host test task)
.\gradlew :shared:core:network:testAndroidHostTest --tests "com.inwords.expenses.core.network.RequestRetryTest"

# Note: module host-test tasks usually use :<module>:testAndroidHostTest (not :<module>:testHostTest)

# Run instrumented tests (requires device/emulator)
.\gradlew :app:connectedAutotestAndroidTest

# Run device tests (requires device/emulator) (includes Room tests)
.\gradlew connectedAndroidDeviceTest

# Code coverage (Kover Aggregated): all modules (app + shared host tests). Requires -Pkover; report at build/reports/kover/
# Kover is configured in settings.gradle.kts only (settings plugin), not in root or app build.gradle.kts. Report excludes :baselineprofile, :benchmarks, :benchmarks:databases.
.\gradlew testHostTest :app:test -Pkover koverHtmlReport koverXmlReport

# Run instrumented tests with Gradle Managed Devices
./gradlew :app:pixel6Api35AtdAutotestAndroidTest "-Dcom.android.tools.r8.disableApiModeling=true"

# Run device tests with Gradle Managed Devices (includes Room tests)
./gradlew :app:pixel6Api35AtdAndroidDeviceTest
```

#### Code Quality

```powershell
# Run lint analysis (40 seconds)
.\gradlew lint --continue

# Run lint on specific build variants
.\gradlew lintDebug
.\gradlew lintRelease
.\gradlew lintBenchmarkRelease

# Apply lint auto-fixes
.\gradlew lintFix

# Update lint baseline (if using lint baseline files)
.\gradlew updateLintBaseline

# Generate lint reports at: app/build/reports/lint-results-debug.html
```

#### Dependency Management

```powershell
# Check for dependency updates (5+ minutes)
.\gradlew dependencyUpdates --refresh-dependencies -Drevision=release

# Report location: build/dependencyUpdates/report.txt
```

### Build Warnings and Issues

**Expected Warnings (safe to ignore):**

- `WARNING: The option setting 'android.r8.optimizedResourceShrinking=true' is experimental`
- `Calculating task graph as configuration cache cannot be reused because file 'gradle\buildSrc.versions.toml' has changed`
- Cronet namespace warnings in manifest merger
- Redundant visibility modifier warnings from generated Room code
- Native library stripping warnings for specific .so files
- `OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes` during unit tests
- `Parallel Configuration Cache is an incubating feature` warnings

**Build Process Notes:**

- Configuration cache is enabled and may show "incubating feature" warnings
- First build after clean takes ~28 seconds
- Incremental builds are much faster due to Gradle caching
- KSP generates code for Room DAOs and may show redundant modifier warnings
- Dependency updates command may take 5+ minutes and should not be interrupted
- PowerShell users: Use `;` instead of `&&` for command chaining
- PowerShell users: Quote `-D...` Gradle properties (for example `"-Dcom.android.tools.r8.disableApiModeling=true"`) to avoid accidental task parsing

## Project Architecture (Reference)

### Module Structure

```
app/                          # Android application module
shared/                       # Kotlin Multiplatform shared code
  ├── core/                   # Core utilities and infrastructure
  │   ├── ui-design/          # Design system and themes
  │   ├── navigation/         # Navigation components with deep linking
  │   ├── network/            # HTTP client configuration (Ktor + Cronet)
  │   ├── locator/            # Dependency injection container
  │   ├── utils/              # Common utilities
  │   ├── storage-utils/      # Database utilities
  │   ├── ui-utils/           # Compose UI utilities
  │   └── ktor-client-cronet/ # Custom Cronet Engine implementation for Ktor
  ├── feature/                # Feature modules
  │   ├── events/             # Event management (create, join, add participants to existing event or during event creation)
  │   │   └── ui/
  │   │       ├── add_persons/        # Add participants during event creation
  │   │       ├── add_participants/   # Add participants to existing event
  │   │       ├── choose_person/      # Choose current person (participant)
  │   │       ├── create/             # Create new event
  │   │       ├── join/               # Join existing event
  │   │       └── ...
  │   ├── expenses/           # Expense tracking (recording, debts, splits)
  │   ├── settings/           # App settings
  │   ├── menu/               # Navigation menu
  │   ├── share/              # Sharing functionality
  │   └── sync/               # Background sync with WorkManager
  └── integration/            # Platform integration
      ├── base/               # Main navigation host and app setup
      └── databases/          # Room database implementation
iosApp/                       # iOS application (SwiftUI)
baselineprofile/              # Android performance profiling
buildSrc/                     # Build logic and plugins
gradle/                       # Version catalogs and properties
```

### Key Configuration Files

- `gradle/shared.versions.toml` - Shared dependency versions
- `gradle/buildSrc.versions.toml` - Build plugin versions
- `buildSrc/src/main/kotlin/` - Custom Gradle plugins
- `app/proguard-rules.pro` - R8/ProGuard configuration
- `gradle.properties` - Build optimization settings

### Custom Gradle Plugins

- `shared-library-plugin` - Android library module defaults
- `shared-kmm-library-plugin` - KMM module configuration

### Important File Locations

- **Main Activity:** `app/src/main/kotlin/ru/commonex/ui/MainActivity.kt`
- **App Application:** `app/src/main/kotlin/ru/commonex/App.kt`
- **Android AppFunctions entry point:** `shared/integration/base/src/androidMain/kotlin/com/inwords/expenses/integration/base/appfunctions/CommonExAppFunctions.kt`
- **iOS App:** `iosApp/iosApp/iOSApp.swift`
- **Manifest:** `app/src/main/AndroidManifest.xml` (includes deep linking config)
- **ProGuard:** `app/proguard-rules.pro` (minimal rules for Cronet and protobuf)
- **ProGuard:** `app/proguard-rules-autotest.pro` (rules for android tests)
- **ProGuard:** `app/proguard-test-rules.pro` (rules for android tests)

### Version Catalog Structure

- **shared.versions.toml:** Main dependencies (Kotlin, Compose, Room, Ktor, etc.)
- **buildSrc.versions.toml:** Build plugins (AGP, Kotlin compiler)
- Centralized version management prevents conflicts across 50+ modules

## Development Guidelines (Reference)

### Making Changes

1. **Always run tests after changes:** `.\gradlew test`
2. **Check lint issues:** `.\gradlew lint --continue`
3. **For KMM modules:** Changes in `shared/` affect both Android and iOS
4. **Generated code:** Room DAOs are auto-generated by KSP, don't edit manually
5. **Avoid duplicating code:** Prefer delegating to existing logic (e.g. domain interactors) instead of reimplementing in integration layers
6. **Required persistence state:** Do not use nullable Room entity fields or local-store return types for required persisted state; initialize them in fresh-create and migration paths instead of modeling them as `T?`
7. **Room data updates:** Prefer targeted DAO `UPDATE` queries over reading the row and rebuilding it via `upsert`

### Component Factory Deps Pattern

- For KMP `*ComponentFactory` APIs, if `expect`/`actual` `Deps` repeat the same shared members across platforms, extract those members into `*ComponentFactoryCommonDeps` in `commonMain` and make platform `Deps` extend it.
- Keep only platform-specific members inline in platform `actual interface Deps` declarations.
- Do not introduce `*CommonDeps` when the common `Deps` contract is empty; keep those factories as-is to avoid boilerplate.

### Code Generation

- **Room database:** Uses KSP for DAO generation (expect redundant visibility warnings)
- **Wire protocol buffers:** Used for settings serialization
- **Compose compiler:** Enabled for all modules with Compose UI

### Common Issues and Solutions

- **Build fails after dependency changes:** Run `.\gradlew clean` first
- **KSP errors:** Usually resolved by clean build
- **Version conflicts:** Check `gradle/shared.versions.toml` for centralized versions
- **iOS build issues:** Ensure Xcode is properly configured for the `iosApp` module

### Package Structure

- **Main package:** `ru.commonex` (Android), `com.inwords.expenses` (shared)
- **Namespace pattern:** Feature-based organization (`com.inwords.expenses.feature.{feature-name}`)
- **MainActivity:** `ru.commonex.ui.MainActivity`

### Coding Patterns (Reference)

See `android/docs/patterns.md` for ViewModel, Compose UI, state modeling, form input patterns, and architecture/wiring style conventions.

### Performance Considerations

- **Baseline profiles:** Module at `baselineprofile/` for Android startup optimization
- **R8 optimization:** Enabled for release builds with custom ProGuard rules
- **Cronet networking:** Using Chrome's network stack for better performance
- **Shared HTTP client lifecycle:** `NetworkComponent` lazily creates and reuses a single Ktor `HttpClient` instance for shared mobile code; avoid bypassing it with ad-hoc client construction in feature modules.
- **Network logging:** Android HTTP logging is enabled only for non-production builds through `NetworkComponentFactory`.

### Testing Strategy

- **Unit tests:** JUnit 6 for host/JVM tests
- **Instrumented tests (non-UI):** Android Tests with JUnit 6
- **Instrumented tests (Compose UI E2E tests):** Android Tests with JUnit 4 And Marathon. `ComposeTestRule` with context receivers pattern. Run against the real backend; avoid mocks and hardcoded remote fixtures by creating required data in-test.
- **Room tests:** use `androidx.room:room-testing`/`MigrationTestHelper` for migration validation only (example `MigrationTest.kt` in `androidDeviceTest` source set).
- **KMM library host tests:**
    - For shared-module Android host tests that assert `Flow` emissions, prefer Turbine (`app.cash.turbine`) over launching background collectors inside `runTest`; this avoids subscription-timing false negatives.
    - Test the class through its constructor dependencies and assert collaborator calls at the class boundary.
    - Do not copy production flow pipelines into tests, and do not drop below the class boundary into infrastructure such as WorkManager when the class can be isolated directly. If a collaborator is difficult to mock in host tests (for example expect/actual
      manager types), add a minimal boundary seam for the observer-facing methods rather than mocking deeper platform infrastructure.
    - ViewModel host-test stability: see `android/docs/patterns.md` (ViewModel host-test stability).
- **Network 409 retry policy:**
    - See [`docs/network-contracts.md`](../docs/network-contracts.md) for the repo-wide transport contract and current retry/error-mapping rules.
    - Validate with `.\gradlew :shared:core:network:testAndroidHostTest --tests "com.inwords.expenses.core.network.RequestRetryTest"`.
- **KMM library device tests:**
    - For `shared:integration:base` AppFunctions tests, run `.\gradlew :shared:integration:base:connectedAndroidDeviceTest`.
    - This module's `androidDeviceTest` source set uses `io.mockk:mockk-android` and `execution = "HOST"` because orchestrator-based discovery did not report results correctly for this module.
    - This path was validated on both API 35 and API 36 emulators in-session.
- **Device testing:** Managed devices configured in `pixel6Api35*` tasks
- **Marathon runner:** Cross-platform test runner for CI with retries and sharding

### Instrumented Test Architecture

The instrumented tests use a **Page Object / Screen Object pattern** with Kotlin context receivers:

```
app/src/androidTest/kotlin/ru/commonex/
├── BasicInstrumentedTest.kt      # Main test class with @RunWith(AndroidJUnit4::class)
├── ConnectivityRule.kt           # JUnit 4 Rule for network control (@Offline annotation)
├── ConnectivityManager.kt        # Shell commands for wifi/data control
├── testUtils.kt                  # runTest utility for reducing boilerplate
└── screens/                      # Screen objects using context receivers
    ├── BaseScreen.kt             # Base class with common wait/assert helpers
    ├── ExpensesScreen.kt
    ├── LocalEventsScreen.kt
    └── ...
```

**Key patterns:**

1. **Context receivers for ComposeTestRule:** Screen methods use `context(rule: ComposeTestRule)` to access the test rule without explicit parameter passing:
   ```kotlin
   context(rule: ComposeTestRule)
   suspend fun clickCreateEvent(): CreateEventScreen {
       rule.onNodeWithText(label).performClick()
       return CreateEventScreen()
   }
   ```

2. **Test structure with RuleChain:** Tests use `RuleChain` to order rules correctly:
   ```kotlin
   private val composeRule = createAndroidComposeRule<MainActivity>()
   private val connectivityRule = ConnectivityRule()

   @get:Rule
   val ruleChain: RuleChain = RuleChain
       .outerRule(connectivityRule)
       .around(composeRule)
   ```

3. **Utility for test execution:** Tests use `runTest` extension from `testUtils.kt` to reduce boilerplate:
   ```kotlin
   @Test
   fun testSomeFlow() = composeRule.runTest {
       LocalEventsScreen()
           .clickCreateEvent()
           .enterEventName("Test")
           // ...
   }
   ```
   This utility wraps the test in `runBlocking` and provides `ComposeTestRule` as a context receiver.

   **Note:** `TestScope`/`StandardTestDispatcher` cannot be used with Compose because UI operations must run on the main thread. For instrumented tests, `runBlocking` is appropriate since the device/emulator runs in real-time anyway.

4. **@Offline annotation:** Custom annotation + `ConnectivityRule` for tests requiring network control:
   ```kotlin
   @Offline
   @Test
   fun testOfflineFlow() = runBlocking { ... }
   ```
5. **Selectors:** Prefer test tags for new selectors, fall back to resource strings, and avoid raw literals unless unavoidable. For overlays (dialogs/bottom sheets), scope checks to overlay-specific tags; do not rely on global text waits that can match
   underlying screens.

## Common Development Tasks (Workflow)

### Adding a New Feature Module

1. Create module under `shared/feature/`
2. Apply `shared-kmm-library-plugin` in build.gradle.kts
3. Add to `settings.gradle.kts` includes
4. Follow naming pattern: `com.inwords.expenses.feature.{feature-name}`

### Adding a New Entity (within a feature module)

1. Create entity data models in the feature module's data layer (e.g., `shared/feature/{feature-name}/src/commonMain/kotlin/.../data/db/entities/`)
2. Add Room entity annotations if database persistence is needed
3. Create repository interfaces in the domain/data layer
4. Implement repositories with Room DAOs (DAOs are auto-generated by KSP)
5. Update database schema in `shared/integration/databases` if needed
6. Add data models and DTOs for network communication if the entity is synced with backend

### Working with Database

- Entities are defined in feature modules (e.g., `shared/feature/events/src/commonMain/kotlin/.../data/db/entities/`)
- DAOs are generated by Room with KSP, do not edit generated files
- Database setup is in `shared/integration/databases`
- **Database migrations** are in `shared/integration/databases/src/commonMain/kotlin/.../data/migration/`
- **Initial data seeding** is in `RoomOnCreateCallback` for new installs
- **Database performance research**: follow `android/docs/database-benchmarking.md`; run in `:benchmarks:databases` only.
- **Benchmark execution policy**: decision-grade DB results run on connected real device; emulator runs are provisional and must be marked as such.
- **Benchmark run mode**: default is full-suite batch run (`connectedReleaseAndroidTest` or managed-device equivalent) repeated 5 independent process-level runs; class-by-class runs are only for disputed/noisy metrics validation.
- **Benchmark code lifecycle**: keep a maintainable permanent suite in `android/benchmarks/databases/src/androidTest/.../benchmark/` (`core`, `template`, `scenarios/*`). Refactor classes as scenarios evolve; do not delete scenario classes after a run.
- **Benchmark artifacts**: save benchmark outputs under `android/docs/artifacts/<research-id>/`; for multi-batch studies keep per-batch minimal pairs (`combined-summary-timeNs.csv`, `readable-ab-deltas.csv`) plus cross-batch aggregates (
  `aggregate-<N>runs-ab-deltas.csv`, `aggregate-<N>runs-absolute-medians.csv`). Use `android/benchmarks/databases/tools/run_connected_benchmark_suite.py` as the primary runner.

#### Database Migrations

When adding new data or schema changes for existing users:

1. **Create migration constant** in `shared/integration/databases/src/commonMain/kotlin/com/inwords/expenses/integration/databases/data/migration/`
    - Name pattern: `Migration{N}To{N+1}.kt` (e.g., `Migration1To2.kt`)
    - Use `internal val MIGRATION_{N}_{N+1} = object : Migration(startVersion, endVersion) { ... }` pattern
    - Override `migrate(connection: SQLiteConnection)` method
    - Use `connection.execSQL()` for SQL operations
    - **Make migrations idempotent** for inserted rows, and keep any seeded reference data aligned with `RoomOnCreateCallback`

2. **Update database version** in `AppDatabase.kt`:
    - Increment `version` in `@Database` annotation
    - Import the migration constant: `import com.inwords.expenses.integration.databases.data.migration.MIGRATION_1_2`
    - Register migration: `.addMigrations(MIGRATION_1_2)` in `createAppDatabase()`

3. **Example migration pattern** (for adding currency):
   ```kotlin
   internal val MIGRATION_1_2 = object : Migration(1, 2) {
       override fun migrate(connection: SQLiteConnection) {
           connection.execSQL(
               """
               INSERT INTO currency (currency_server_id, code, name, rate_unscaled, rate_scale) 
               SELECT NULL, 'AED', 'UAE Dirham', ..., ...
               WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'AED')
               """.trimIndent()
           )
       }
   }
   ```

4. **For new installs**: Add matching seeded data to `RoomOnCreateCallback.onCreate()` so new users get the same offline-capable snapshot immediately

**Migration tests (Android instrumented)**: `MigrationTestHelper.createDatabase()` bypasses Room callbacks, so seed base data manually (e.g., currencies) or invoke `RoomOnCreateCallback.onCreate()` with the SQLite connection before running migrations. Keep
this helper scoped to migration verification, not performance benchmarking.

### Adding Dependencies

- Update version catalogs in `gradle/shared.versions.toml` or `gradle/buildSrc.versions.toml`
- Use catalog references in build files: `implementation(shared.some.library)`
- Maintain KMP compatibility for shared modules

### UI Development

- Follow Material 3 Expressive guidelines and design system from `shared:core:ui-design`
- Compose Multiplatform for shared UI components
- Platform-specific implementations in `androidMain`/`iosMain` source sets
- For navigation, use Navigation 3 library with helpers from `shared:core:navigation`

### Compose Material 3 UI/UX Rules (Strict)

For any Android Compose UI change (new or edited pane/screen/dialog/bottom sheet), all items below are required and blocking:

1. **Screen Anatomy and Structure:** Full-screen surfaces must use `Scaffold` and place top bar/actions/snackbar through scaffold slots.
2. **Insets and Edge-to-Edge Safety:** Apply scaffold/system/IME insets once at the content root. No clipped content under bars, cutouts, gesture areas, or keyboard.
3. **Guardrails and Grid:** Use Material mobile guardrails and consistent spacing rhythm (default side margins around `16dp`, layout spacing on an `8dp` grid, smaller adjustments on `4dp`).
4. **Alignment and Grouping:** Keep related content/actions visually grouped via spacing, cards, and dividers; maintain consistent horizontal alignment.
5. **Content Hierarchy:** Keep section structure clear (title/supporting text/content/actions) and avoid dense, ungrouped controls.
6. **Primary Action Hierarchy:** Keep one dominant primary action per surface. Destructive actions must be secondary unless in a confirmation dialog.
7. **Navigation and Action Placement:** Put global/screen actions in app bars, primary task actions in FAB/primary buttons, and infrequent actions in overflow or secondary surfaces.
8. **Adaptive Layouts:** Use window size classes/canonical adaptive patterns for layout decisions (single-pane on compact; supporting pane/list-detail where appropriate on larger sizes).
9. **Lazy vs Scroll Containers:** Use `LazyColumn` (or other lazy containers) for dynamic/unbounded collections. Use `verticalScroll` only for bounded short forms/details.
10. **Overlay Semantics:** Use dialog for confirmation/critical interruption, and bottom sheet for contextual details/actions.
11. **State Coverage:** Define and handle `Loading`/`Success`/`Error`/`Empty` states where applicable. No silent blank states.
12. **Accessibility Semantics:** Interactive icons and controls must have meaningful semantics (including `contentDescription` where needed).
13. **Interaction Feedback:** Long-running actions must show progress and prevent duplicate taps while in progress.
14. **Preview and Testability:** Complex panes must have previews and stable test tags/selectors for critical actions.
15. **Typography Roles:** Use `MaterialTheme.typography` role styles (`display`, `headline`, `title`, `body`, `label`) instead of arbitrary text styles.
16. **Type Scale Discipline:** Avoid hard-coded `sp` sizes and ad-hoc line heights/letter spacing unless required for a documented one-off design reason.
17. **Readable Font Choices:** Prefer app theme font families optimized for UI readability; avoid decorative fonts in core task flows.
18. **Color Role Usage:** Use `MaterialTheme.colorScheme` semantic roles (`primary`, `onPrimary`, `surface`, `onSurface`, etc.) and avoid raw hex colors in feature UI.
19. **Contrast and Meaning:** Preserve readable contrast and never rely on color alone to communicate critical state/action meaning.
20. **Surface Hierarchy:** Use surface/container roles (`surface`, `surfaceContainer*`) to express elevation and grouping, not ad-hoc background tints.
21. **Shape Tokens:** Use `MaterialTheme.shapes` (or M3 shape scale tokens) for corner radii; avoid arbitrary radius values unless design-reviewed.
22. **Component Shape Mapping:** Respect M3 default shape intent (e.g., chips/buttons/cards/text fields) and document intentional overrides.
23. **Border Semantics:** Use borders mainly for outlined/medium-emphasis components (for example, outlined buttons/cards/text fields), not as decorative noise.
24. **Elevation Strategy:** Prefer tonal elevation and component defaults; add shadow elevation only when depth separation is necessary.
25. **Shadow Restraint:** Keep shadows subtle and consistent; avoid stacking multiple custom shadows that reduce clarity or legibility.
26. **Minimum Touch Targets:** Interactive controls must remain at least `48dp x 48dp` touch size.
27. **Motion Purpose and Control:** Use animation to support comprehension (state/visibility/layout transitions), not decoration. UX must remain clear when system animation scale is reduced or disabled.
28. **Accessibility Traversal Order:** Keep logical reading order by default; when needed, explicitly control order with semantics (`isTraversalGroup`, `traversalIndex`) so screen-reader traversal matches visual intent.
29. **Font Scaling Robustness:** UI must remain usable at high font scales (including Android nonlinear scaling up to 200%): no clipped/overlapped critical text and no blocked primary actions.
30. **Localization in Compose:** Do not hardcode user-facing strings in UI code; use resource APIs (`stringResource`, `pluralStringResource`) and keep default resources complete.
31. **RTL and Pseudolocale Verification:** Validate key screens with pseudolocales (including RTL pseudolocale) to catch truncation, mirroring, and direction issues before merge.
32. **Text Input Keyboard Semantics:** For each text input, intentionally set keyboard behavior (`keyboardType`, `imeAction`, `capitalization`, `autoCorrect`) according to task semantics.
33. **Input Constraints and Formatting:** Prefer text input transformations for constraints/formatting (length, allowed characters, output formatting) over ad-hoc post-processing.
34. **Adaptive Layout Decisions:** Use window size classes for layout decisions (not device type checks); support canonical adaptive patterns (for example list-detail / supporting pane) on larger widths.
35. **Lazy List Performance Contracts:** For dynamic lists, provide stable item keys and `contentType` where relevant to maximize reuse and reduce recomposition cost.
36. **Compose Stability and Recomposition Hygiene:** Keep expensive work out of composition (`remember`, `derivedStateOf`, ViewModel precomputation), and favor stable/immutable UI models to reduce unnecessary recompositions.
37. **Composable API Semantics:** For reusable composables, prefer semantic API parameters (`isEmphasized`, `variant`, `enabled`) over visual implementation parameters (`TextStyle`, `Color`, raw typography tokens), unless a visual parameter is explicitly
    required by a shared design-system contract.
38. **Modifier-First Layout Extensibility:** For reusable composables, expose a `modifier: Modifier = Modifier` and use it for spacing/placement overrides at call sites; avoid bespoke spacing params such as `topPadding` on component APIs.
39. **Spacing Token Discipline:** Use standard 4dp-grid spacing tokens only (`4/8/12/16/24/32`, plus `48` for touch-target-related sizing). Avoid ad-hoc spacing values (for example `10dp`, `14dp`, `18dp`) unless explicitly justified in a nearby code comment.
40. **UX Scope Control:** During UI polish/refactor tasks, do not add new controls/actions (for example extra close buttons, additional menus, or secondary actions) unless explicitly requested or required to satisfy an existing acceptance criterion.

Allowed exceptions are only explicit domain constraints where the rule does not apply (for example, no empty state by domain model). Document the reason in code comments when using an exception.

### Event Sharing

Events are shared via secure token-based links that expire in 14 days:

- **Primary flow**: Generate share token via `CreateShareTokenUseCase` when user clicks "Share" or "Copy"
- **Fallback flow**: If token generation fails (offline/network error), use PIN-based link with warning message
- **Share messages**: Localized with formatted expiration dates
- **Deeplinks**: Support both `?token=` (new secure method) and `?pinCode=` query parameters
- **Join flow**: Deeplinks with either `token` or `pinCode` auto-trigger join without requiring manual PIN entry
- **Error handling**: Token expiration and invalid token errors are displayed inline in JoinEventPane
- **Location**: `shared/feature/menu/` (share UI), `shared/feature/events/domain/CreateShareTokenUseCase.kt` (use case)
- **Key files**:
    - `MenuViewModel.kt` - Share/copy logic with token generation and fallback
    - `MenuDialog.kt` - UI with share button, loading indicator and clipboard copy button
    - `CreateShareTokenUseCase.kt` - Domain use case for token generation
    - `JoinEventViewModel.kt` - Handles token- and pinCode-based deeplink joining

#### Deeplink Instrumented Tests (Android)

- **Location**: `android/app/src/androidTest/kotlin/ru/commonex/BasicInstrumentedTest.kt`
- **Framework**: JUnit 4 (required for Marathon compatibility)
- **Covers**: share link generation, clipboard extraction, local event removal, and deeplink auto-join for both `token` and `pinCode`.
- **Prereqs**:
    - Device/emulator must allow clipboard access in tests.
    - Network toggling uses `svc wifi/data` via instrumentation (see `ConnectivityManager`); avoid running on devices where these shell commands are blocked.
- **Implementation notes**:
    - Tests use `RuleChain` with `ConnectivityRule` (outer) and `createAndroidComposeRule` (inner).
    - Screen objects use Kotlin context receivers: `context(rule: ComposeTestRule)`.
    - Wait for Copy to become enabled before reading clipboard (`MenuDialogScreen.waitUntilCopyEnabled()`).
    - Deeplink tests expect `https://commonex.ru/event/{id}?token=...` or `?pinCode=...` extracted from clipboard text.
    - Use `composeRule.activityRule.scenario.onActivity { it.onNewIntent(intent) }` to feed deeplinks.

#### Combining Instrumented Tests

- Prefer **scenario-composed tests** when they reduce setup cost and cover realistic flows (e.g., create → share token → remove local copy → join via deeplink).
- Combine steps **only if** the flow is coherent and failure localization remains clear; keep each test’s intent obvious from its name and comments.
- When combining, keep assertions at each critical transition (creation, share link, deletion, join) so failures are easy to pinpoint.
- Avoid duplicate assertions between steps; keep each check unique to its transition.
- Don’t hesitate to modify existing tests when they’re a better fit for new scenario checks; keep changes minimal and maintain clarity.

### Adding Participants to Existing Events

The "Add participants" feature allows users to add new participants to an existing event:

- **Entry point**: Menu dialog → "Add participants" option
- **UI**: Full-screen pane (`AddParticipantsToEventPane`) similar to the one used in event creation flow
- **Domain**: `AddParticipantsToCurrentEventUseCase` handles adding participants locally
- **Sync**: New participants are stored locally immediately (offline-first) with `serverId = null`
- **Background sync**: `EventPersonsPushTask` automatically syncs new participants to server
- **Location**: `shared/feature/events/src/commonMain/kotlin/.../ui/add_participants/`
- **Key files**:
    - `AddParticipantsToEventPane.kt` - UI composable
    - `AddParticipantsToEventViewModel.kt` - ViewModel with state management
    - `AddParticipantsToCurrentEventUseCase.kt` - Domain use case
    - `AddParticipantsToEventPaneDestination.kt` - Navigation destination

The confirm button is disabled when there are no participants or all participant names are empty (checked via `isConfirmEnabled` computed property).

### Adding a New Currency

When adding support for a new currency (e.g., AED), update all the following:

1. **Seeded local currency snapshot for new installs**: `shared/integration/databases/src/commonMain/kotlin/.../data/RoomOnCreateCallback.kt`
    - Add `INSERT INTO currency` statement in `onCreate()` method
    - Seed `rate_unscaled` and `rate_scale` with the initial offline-capable rate snapshot
    - Use sequential ID (next available number)

2. **Existing users**: Create migration in `shared/integration/databases/src/commonMain/kotlin/.../data/migration/`
    - Create `Migration{N}To{N+1}.kt` file with `internal val MIGRATION_{N}_{N+1}` constant
    - Backfill both the currency row and the persisted rate columns for upgraded installs
    - Increment database version in `AppDatabase.kt`
    - Import and register migration: `.addMigrations(MIGRATION_1_2)` in `createAppDatabase()`

3. **Shared currency refresh contract**: `shared/feature/events/src/commonMain/kotlin/.../data/network/store/CurrenciesRemoteStoreImpl.kt`
    - Keep `/api/v3/user/currencies/all` parsing aligned with the backend payload (`currencies` plus `exchangeRate`)
    - Preserve the conditional-fetch `ETag` behavior used by mobile currency sync

4. **UI preview/mock data**: Update preview functions in UI components
    - `CreateEventPane.kt` - `CreateEventPanePreview()` function
    - `AddExpensePane.kt` - `mockAddExpenseScreenUiModel()` function
    - Any other UI components with currency lists in previews

**Note**: The migration ensures existing users get the currency and its initial persisted rate snapshot on app update, while `RoomOnCreateCallback` ensures new installs have the same offline-capable data from the start. `CurrencyExchanger` now reads
Room-backed rates rather than a hardcoded production map.

### Known TODOs and Technical Debt

- Several "TODO mvp" comments indicate MVP-level implementations that need improvement
- Some String vs Double type inconsistencies in network DTOs
- User agent configuration needs finalization in HTTP client

## Debugging and Troubleshooting (Reference)

### Build Issues

- **Clean builds solve most KSP issues:** `.\gradlew clean`
- **Memory problems:** Check JVM args in gradle.properties (currently set to 2GB)
- **Parallel build issues:** Parallel execution enabled, may cause race conditions
- **Configuration cache:** Can be cleared by deleting `.gradle/configuration-cache/`

### Runtime Issues

- **Network:** Uses Cronet embedded, check manifest for network permissions
- **Database:** Room migrations handled automatically, check for schema changes
- **Deep linking:** App handles commonex.ru domain, test with intent filters
- **Background sync:** WorkManager requires proper initialization
- **Expense details exchange rate crash (`Negative decimal precision is not allowed`):** Avoid formatting computed rates via `BigDecimal.scale(...)`-based helpers. Use `DecimalMode` division + `roundToDigitPositionAfterDecimalPoint(2, ...)` and fixed-scale
  numeric formatting. See `android/docs/patterns.md` (`Numeric BigDecimal Patterns`).

### Performance Debugging

- **Baseline profiles:** Generated in `baselineprofile/` module for startup optimization
- **R8 optimization:** Check `app/proguard-rules.pro` for keep rules
- **Build times:** First build ~28s, incremental much faster with Gradle cache

## Environment Setup (Reference)

### Required Tools

- **JDK:** Same as CI (see `.github/workflows/android.yml`). Project targets JVM 17.
- **Android SDK:** API level as in `app/build.gradle.kts`. **Gradle:** use wrapper (version in `gradle/wrapper/gradle-wrapper.properties`), do not install separately.
- **Git** for version control. Full list: [`docs/local-agent-prerequisites.md`](docs/local-agent-prerequisites.md).

### IDE Configuration

- **Android Studio** recommended for Android and KMM development
- Enable Kotlin Multiplatform plugin
- Configure JAVA_HOME to match the JDK used in CI (see workflow file)

## Validation Steps (Workflow)

### MCP-First Validation Policy

- Use JetBrains MCP tools as the default validation path when MCP is available for the open Android project.
- Prefer `execute_terminal_command` (MCP terminal) for compile/build/test validation after edits (for example running Gradle tasks).
- Prefer `get_file_problems` for per-file diagnostics on edited files.
- Prefer other MCP IDE-aware tools for project queries and developer actions when relevant (for example: `get_run_configurations`, `execute_run_configuration`, `search_in_files_by_text`, `search_in_files_by_regex`, `list_directory_tree`,
  `get_project_modules`, `get_project_dependencies`).
- If MCP is unavailable or limited, run equivalent Gradle/CLI validation and explicitly note the fallback reason in the report.

Before submitting changes, run these validation steps:

```powershell
# 1. Build
.\gradlew assembleDebug

# 2. Run all unit tests (25 seconds)
.\gradlew test

# 3. Run KMM host tests (~10 seconds)
.\gradlew testHostTest

# 4. Check code quality (30-48 seconds)
.\gradlew lint --continue

# 5. Verify KMM targets compile (iOS: iosArm64, iosSimulatorArm64 only)
.\gradlew :shared:integration:base:linkDebugFrameworkIosSimulatorArm64

# If iOS tests are added, run iosSimulatorArm64Test (do not run iosX64Test)
# .\gradlew iosSimulatorArm64Test

# 6. Build release variant (includes R8 optimization)
.\gradlew assembleRelease

# 7. Build autotest variant (release-like, includes R8 optimization, but no shrinking and no obfuscation)
.\gradlew assembleAutotest

# 8. Optional: Run instrumented tests (requires device/emulator)
.\gradlew :app:connectedAutotestAndroidTest "-Dcom.android.tools.r8.disableApiModeling=true"

# 9. Optional: Run managed device tests (local Gradle Managed Devices testing)
.\gradlew :app:pixel6Api35AtdAutotestAndroidTest "-Dcom.android.tools.r8.disableApiModeling=true"

# 10. Optional: Run instrumented tests with Marathon (requires device/emulator + marathon CLI) (see Marathon doc for details)
```

### Quick Validation (for small changes)

```powershell
# Fast validation for minor changes (~15 seconds total)
.\gradlew testHostTest
.\gradlew lintDebug

# Sync observer host-test regression
.\gradlew :shared:feature:sync:testAndroidHostTest --tests "com.inwords.expenses.feature.sync.domain.EventsSyncObserverTest"

# Network retry host-test regression
.\gradlew :shared:core:network:testAndroidHostTest --tests "com.inwords.expenses.core.network.RequestRetryTest"
```

### Comprehensive Testing (before major releases)

```powershell
# Full test suite with aggregated reporting
.\gradlew allTests
.\gradlew allDevicesCheck
.\gradlew lint --continue
```

**Trust these instructions:**

- Only search for additional information if you encounter specific errors not covered here or if dependency/build tool versions have changed significantly.
- The build system is well-configured and should work reliably when following these steps.

**Freshness:**

- Per root `AGENTS.md`, search official current docs for library/tool versions, migrations, and version-specific errors when needed.
- Do not search for repo-local conventions already documented here. If upstream docs conflict with this file, flag the conflict.

Consider these rules if they affect your changes.
