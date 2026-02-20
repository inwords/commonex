# Agent Instructions for Expenses (CommonEx) Android Project

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
- Gradle 9.2.1 with Kotlin DSL and version catalogs
- Android Gradle Plugin 9.0.0-rc03
- Target: Android API 36, Min API 26

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

- Release workflow: use the `prepare-android-release` skill at `android/.agents/skills/prepare-android-release` for version bump, baseline profiles, and tagging.

## Tooling Docs (Reference)

- `android/marathon/README.md` - Local Marathon runner usage and setup notes (library extracted manually; binaries not in git). Requires JUnit 4 annotations for test discovery; config in `android/Marathonfile`.
- `android/gradle/README.md` - Gradle Profiler benchmarks and scenarios (`android/gradle/performance.scenarios`; profiler distribution in `android/gradle/profiler`).
- `android/docs/database-benchmarking.md` - Room benchmark workflow and correctness checklist. Run DB benchmarks from dedicated module `:benchmarks:databases` (AndroidBenchmarkRunner + non-debuggable release test build type). Standard flow: template ->
  scenario classes -> full-suite process-level runs -> aggregate artifacts -> log conclusions.
- `android/docs/database-research-log.md` - Latest decision-grade database benchmark conclusions and artifact references.
- `android/docs/database-research-log-template.md` - Canonical entry template for new items in the database research log.

## Build Instructions (Workflow)

### Prerequisites

- JDK 22+ (Project uses JVM target 17, daemon configured for JDK 22/Temurin)
- Android SDK with API 36
- Gradle 9.2.1 (use wrapper)

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

# Run instrumented tests (requires device/emulator)
.\gradlew :app:connectedAutotestAndroidTest

# Run device tests (requires device/emulator) (includes Room tests)
.\gradlew connectedAndroidDeviceTest

# Run instrumented tests with Gradle Managed Devices
./gradlew :app:pixel6Api35AtdAutotestAndroidTest -Dcom.android.tools.r8.disableApiModeling

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

See `android/docs/patterns.md` for ViewModel, Compose UI, state modeling, and form input patterns.

### Performance Considerations

- **Baseline profiles:** Module at `baselineprofile/` for Android startup optimization
- **R8 optimization:** Enabled for release builds with custom ProGuard rules
- **Cronet networking:** Using Chrome's network stack for better performance

### Testing Strategy

- **Unit tests:** JUnit 6 for host/JVM tests
- **Instrumented tests (non-UI):** Android Tests with JUnit 6
- **Instrumented tests (Compose UI E2E tests):** Android Tests with JUnit 4 And Marathon. `ComposeTestRule` with context receivers pattern. Run against the real backend; avoid mocks and hardcoded remote fixtures by creating required data in-test.
- **Room tests:** use `androidx.room:room-testing`/`MigrationTestHelper` for migration validation only (example `MigrationTest.kt` in `androidDeviceTest` source set).
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
5. **Selectors:** Prefer test tags for new selectors, fall back to resource strings, and avoid raw literals unless unavoidable.

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
- **Database migrations** are in `shared/integration/databases/src/commonMain/kotlin/.../data/migrations/`
- **Initial data seeding** is in `RoomOnCreateCallback` for new installs
- **Database performance research**: follow `android/docs/database-benchmarking.md`; run in `:benchmarks:databases` only.
- **Benchmark execution policy**: decision-grade DB results run on connected real device; emulator runs are provisional and must be marked as such.
- **Benchmark run mode**: default is full-suite batch run (`connectedReleaseAndroidTest` or managed-device equivalent) repeated 5 independent process-level runs; class-by-class runs are only for disputed/noisy metrics validation.
- **Benchmark code lifecycle**: keep a maintainable permanent suite in `android/benchmarks/databases/src/androidTest/.../benchmark/` (`core`, `template`, `scenarios/*`). Refactor classes as scenarios evolve; do not delete scenario classes after a run.
- **Benchmark artifacts**: save benchmark outputs under `android/docs/artifacts/<research-id>/`; for multi-batch studies keep per-batch minimal pairs (`combined-summary-timeNs.csv`, `readable-ab-deltas.csv`) plus cross-batch aggregates (
  `aggregate-<N>runs-ab-deltas.csv`, `aggregate-<N>runs-absolute-medians.csv`). Use `android/benchmarks/databases/tools/run_connected_benchmark_suite.py` as the primary runner.

#### Database Migrations

When adding new data or schema changes for existing users:

1. **Create migration constant** in `shared/integration/databases/src/commonMain/kotlin/com/inwords/expenses/integration/databases/data/migrations/`
    - Name pattern: `Migration{N}To{N+1}.kt` (e.g., `Migration1To2.kt`)
    - Use `internal val MIGRATION_{N}_{N+1} = object : Migration(startVersion, endVersion) { ... }` pattern
    - Override `migrate(connection: SQLiteConnection)` method
    - Use `connection.execSQL()` for SQL operations
    - **Make migrations idempotent**: Check for existence before inserting data to avoid duplicates

2. **Update database version** in `AppDatabase.kt`:
    - Increment `version` in `@Database` annotation
    - Import the migration constant: `import com.inwords.expenses.integration.databases.data.migrations.MIGRATION_1_2`
    - Register migration: `.addMigrations(MIGRATION_1_2)` in `createAppDatabase()`

3. **Example migration pattern** (for adding currency):
   ```kotlin
   internal val MIGRATION_1_2 = object : Migration(1, 2) {
       override fun migrate(connection: SQLiteConnection) {
           connection.execSQL(
               """
               INSERT INTO currency (currency_server_id, code, name) 
               SELECT NULL, 'AED', 'UAE Dirham'
               WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'AED')
               """.trimIndent()
           )
       }
   }
   ```

4. **For new installs**: Add data to `RoomOnCreateCallback.onCreate()` so new users get it immediately

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

1. **Exchange rates**: `shared/feature/expenses/src/commonMain/kotlin/.../domain/CurrencyExchanger.kt`
    - Add currency code and USD exchange rate to the rates map

2. **New installs**: `shared/integration/databases/src/commonMain/kotlin/.../data/RoomOnCreateCallback.kt`
    - Add `INSERT INTO currency` statement in `onCreate()` method
    - Use sequential ID (next available number)

3. **Existing users**: Create migration in `shared/integration/databases/src/commonMain/kotlin/.../data/migrations/`
    - Create `Migration{N}To{N+1}.kt` file with `internal val MIGRATION_{N}_{N+1}` constant
    - Use idempotent INSERT with `WHERE NOT EXISTS` check
    - Increment database version in `AppDatabase.kt`
    - Import and register migration: `.addMigrations(MIGRATION_1_2)` in `createAppDatabase()`

4. **UI preview/mock data**: Update preview functions in UI components
    - `CreateEventPane.kt` - `CreateEventPanePreview()` function
    - `AddExpensePane.kt` - `mockAddExpenseScreenUiModel()` function
    - Any other UI components with currency lists in previews

**Note**: The migration ensures existing users get the currency on app update, while `RoomOnCreateCallback` ensures new installs have it from the start.

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

### Performance Debugging

- **Baseline profiles:** Generated in `baselineprofile/` module for startup optimization
- **R8 optimization:** Check `app/proguard-rules.pro` for keep rules
- **Build times:** First build ~28s, incremental much faster with Gradle cache

## Environment Setup (Reference)

### Required Tools

- **JDK 22 (Temurin)** (Project targets JVM 17)
- **Android SDK** with API 36 (compile target)
- **Gradle 9.2.1** (use wrapper, do not install separately)
- **Git** for version control

### IDE Configuration

- **Android Studio** recommended for Android and KMM development
- Enable Kotlin Multiplatform plugin
- Configure JAVA_HOME to point to JDK 22

## Validation Steps (Workflow)

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

# 5. Verify KMM targets compile (iOS targets)
.\gradlew iosX64Test iosSimulatorArm64Test

# 6. Build release variant (includes R8 optimization)
.\gradlew assembleRelease

# 7. Build autotest variant (release-like, includes R8 optimization, but no shrinking and no obfuscation)
.\gradlew assembleAutotest

# 8. Optional: Run instrumented tests (requires device/emulator)
.\gradlew :app:connectedAutotestAndroidTest -Dcom.android.tools.r8.disableApiModeling

# 9. Optional: Run managed device tests (local Gradle Managed Devices testing)
.\gradlew :app:pixel6Api35AtdAutotestAndroidTest -Dcom.android.tools.r8.disableApiModeling

# 10. Optional: Run instrumented tests with Marathon (requires device/emulator + marathon CLI) (see Marathon doc for details)
```

### Quick Validation (for small changes)

```powershell
# Fast validation for minor changes (~15 seconds total)
.\gradlew testHostTest
.\gradlew lintDebug
```

### Comprehensive Testing (before major releases)

```powershell
# Full test suite with aggregated reporting
.\gradlew allTests
.\gradlew allDevicesCheck
.\gradlew lint --continue
```

**Trust these instructions.** Only search for additional information if you encounter specific errors not covered here or if dependency/build tool versions have changed significantly. The build system is well-configured and should work reliably when following
these steps.
