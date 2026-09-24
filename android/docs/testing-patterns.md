# Testing Patterns

Detailed testing strategy, architecture, and patterns for the Android/KMM project.

## Testing Strategy

- **Coroutine tests:** Use `kotlinx.coroutines.test.runTest` for Android coroutine tests, including instrumented tests. Compose flows use the `ComposeTestRule.runTest` wrapper described below.
- **Timed benchmark bridge:** The network benchmark retains `runBlocking` inside `measureRepeated` so coroutine-test scheduler setup and teardown are excluded from request measurements. Use `runTest` for coroutine work in benchmark setup and assertions.
- **Unit tests:** JUnit 6 for host/JVM tests
- **Instrumented tests (non-UI):** Android Tests with JUnit 6
- **Instrumented tests (Compose UI E2E tests):** Android Tests with JUnit 4 and Marathon. Prefer Marathon over `:app:connectedAutotestAndroidTest` for local UI-test validation because it matches the retried/sharded runner used in CI. Use `connectedAutotestAndroidTest` only for narrow targeted debugging when Marathon is unavailable or would be unnecessary overhead. Run against the real backend; avoid mocks and hardcoded remote fixtures by creating required data in-test.
- **Device fallback for targeted UI validation:** If `:app:connectedAutotestAndroidTest` is blocked by `No connected devices!`, use the managed-device path `:app:pixel6Api36AtdAutotestAndroidTest` for the same targeted test before reporting completion.
- **Completion bar for UI work:** If you change Compose UI behavior or add/edit instrumented UI flows, do not report completion from compile/host tests alone. Run at least one relevant instrumented UI path and report the exact command and scope that were validated.
- **Deterministic expense timestamps in instrumented tests:** To pin creation time when driving the real add-expense flow, use `com.inwords.expenses.feature.expenses.domain.ExpenseTimeBackdoor.overrideForTests(Instant?)` in a try/finally reset to `null`. Add-expense use cases read this for persisted `timestamp`; referencing a non-existent helper type breaks `:app:compileAutotestAndroidTestKotlin`.
- **Room tests:** use `androidx.room:room-testing`/`MigrationTestHelper` for migration validation only (example `MigrationTest.kt` in `androidDeviceTest` source set).
- **Device testing:** Managed devices configured in `pixel6Api36*` tasks
- **Marathon runner:** Cross-platform test runner for CI with retries and sharding

## KMM Library Host Tests

- For shared-module Android host tests that assert `Flow` emissions, prefer Turbine (`app.cash.turbine`) over launching background collectors inside `runTest`; this avoids subscription-timing false negatives.
- Test the class through its constructor dependencies and assert collaborator calls at the class boundary.
- Do not copy production flow pipelines into tests, and do not drop below the class boundary into infrastructure such as WorkManager when the class can be isolated directly. If a collaborator is difficult to mock in host tests (for example expect/actual
  manager types), add a minimal boundary seam for the observer-facing methods rather than mocking deeper platform infrastructure.
- ViewModel host-test stability: see `android/docs/patterns.md` (ViewModel host-test stability).

## Network 409 Retry Policy

- See [`docs/network-contracts.md`](../../docs/network-contracts.md) for the repo-wide transport contract and current retry/error-mapping rules.
- Validate with `./gradlew --quiet :shared:core:network:testAndroidHostTest --tests "com.inwords.expenses.core.network.RequestRetryTest"`.

## KMM Library Device Tests

- AppFunctions business behavior: `./gradlew --quiet :shared:integration:base:connectedAndroidDeviceTest`. The suite
  calls the KSP-generated `CommonExAppFunctionService`, so it also guards entry-point generation and the context-free
  method signatures.
- AppFunctions registration (generated service, binding permission, intent filter, v2 schema asset property, app
  metadata): `./gradlew --quiet :app:connectedAutotestAndroidTest "-Dcom.android.tools.r8.disableApiModeling=true" "-Pandroid.testInstrumentationRunnerArguments.class=ru.commonex.AppFunctionRegistrationTest"`.
- Both need an API 36 device because the generated service extends the API 36 platform
  `android.app.appfunctions.AppFunctionService`; the CI emulator is described in
  [`local-agent-prerequisites.md`](local-agent-prerequisites.md#api-and-system-image-for-deviceemulator-runs).
- The `shared:integration:base` `androidDeviceTest` source set uses `io.mockk:mockk-android` and `execution = "HOST"`
  because orchestrator-based discovery did not report results correctly for this module.

## Instrumented Test Architecture

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

### Key Patterns

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
   This utility wraps the test in `kotlinx.coroutines.test.runTest` and provides `ComposeTestRule` as a context receiver. It keeps the application's main dispatcher unchanged and uses a five-minute timeout for complete UI flows. Use Compose rule waits for UI and network progress; these use wall-clock time while the test scheduler uses virtual time.

4. **@Offline annotation:** Custom annotation + `ConnectivityRule` for tests requiring network control:
   ```kotlin
   @Offline
   @Test
   fun testOfflineFlow() = composeRule.runTest { ... }
   ```

5. **Selectors:** Prefer test tags for new selectors, fall back to resource strings, and avoid raw literals unless unavoidable. For overlays (dialogs/bottom sheets), scope checks to overlay-specific tags; do not rely on global text waits that can match
   underlying screens.
