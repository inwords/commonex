# Feature Workflows

Step-by-step procedures for common feature development tasks.

## Event Sharing

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

### Deeplink Instrumented Tests (Android)

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

### Combining Instrumented Tests

- Prefer **scenario-composed tests** when they reduce setup cost and cover realistic flows (e.g., create -> share token -> remove local copy -> join via deeplink).
- Combine steps **only if** the flow is coherent and failure localization remains clear; keep each test's intent obvious from its name and comments.
- When combining, keep assertions at each critical transition (creation, share link, deletion, join) so failures are easy to pinpoint.
- Avoid duplicate assertions between steps; keep each check unique to its transition.
- Don't hesitate to modify existing tests when they're a better fit for new scenario checks; keep changes minimal and maintain clarity.

## Adding Participants to Existing Events

The "Add participants" feature allows users to add new participants to an existing event:

- **Entry point**: Menu dialog -> "Add participants" option
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

## Adding a New Currency

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

## Adding a New Feature Module

1. Create module under `shared/feature/`
2. Apply `shared-kmm-library-plugin` in build.gradle.kts
3. Add to `settings.gradle.kts` includes
4. Follow naming pattern: `com.inwords.expenses.feature.{feature-name}`

## Adding a New Entity (within a feature module)

1. Create entity data models in the feature module's data layer (e.g., `shared/feature/{feature-name}/src/commonMain/kotlin/.../data/db/entities/`)
2. Add Room entity annotations if database persistence is needed
3. Create repository interfaces in the domain/data layer
4. Implement repositories with Room DAOs (DAOs are auto-generated by KSP)
5. Update database schema in `shared/integration/databases` if needed
6. Add data models and DTOs for network communication if the entity is synced with backend

## Working with Database

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

### Database Migrations

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
