# Android Runtime Operations

This document is the canonical operational reference for Android app startup behavior, runtime integrations, and Android-only platform services.

## Source Of Truth

- Android application entry point: `android/app/src/main/kotlin/ru/commonex/App.kt`
- Android manifest and startup providers: `android/app/src/main/AndroidManifest.xml`
- Shared runtime bootstrap: `android/shared/integration/base/src/commonMain/kotlin/com/inwords/expenses/integration/base/`
- Android AppFunctions entry points: `android/shared/integration/base/src/androidMain/kotlin/com/inwords/expenses/integration/base/appfunctions/`

## Application Startup

- The Android app uses `ru.commonex.App` as the `Application` class.
- Startup order in `App.onCreate()` is:
    1. initialize Sentry
    2. enable Android `StrictMode` in non-production builds
    3. register shared/platform components
    4. start sync observation

Current production classification:

- `production = !BuildConfig.DEBUG && BuildConfig.BUILD_TYPE != "autotest"`
- `autotest` is treated as non-production at runtime even though it is release-like for R8 optimization.

## Sentry

- Sentry is initialized from shared KMM code through `initializeSentry(production)`.
- The current runtime behavior sets:
    - `environment = production | development`
    - `tracesSampleRate = 0.2` in production
    - `tracesSampleRate = 1.0` in non-production builds
- Android and iOS both use the same shared initialization function, so changes here affect both platforms unless platform-specific branching is added.

## StrictMode

- `StrictMode` is enabled on Android only for non-production builds.
- Current policies:
    - thread policy: `detectAll`, `penaltyLog`, `penaltyFlashScreen`
    - VM policy: `detectAll`, `penaltyLog`
- Release builds do not enable these diagnostics.

## WorkManager

- `App` implements `Configuration.Provider` and supplies a custom WorkManager configuration.
- Manifest setup removes the default `androidx.work.WorkManagerInitializer` from `androidx.startup.InitializationProvider`.
- Current custom configuration:
    - worker coroutine context: `IO`
    - task executor: `IO.limitedParallelism(4)`
    - logging level: `ASSERT` in production, `INFO` otherwise

Operational implication:

- If WorkManager startup behavior changes, update both `App.kt` and `AndroidManifest.xml` together.
- Do not re-enable the default initializer unless the custom configuration is intentionally being removed.

## Sync Bootstrap

- Android startup calls `enableSync()` after components are registered.
- `enableSync()` currently starts event sync observation from a `GlobalScope + IO` coroutine.
- This is operationally important because sync observation starts automatically at app launch; it is not opt-in from a screen.

## AppFunctions

- `App` also implements `AppFunctionConfiguration.Provider`.
- `createAppFunctionConfiguration()` registers `CommonExAppFunctions` as the enclosing AppFunctions service class.
- Current AppFunctions cover:
    - listing currencies
    - creating events
    - listing events
    - calculating debts
    - adding participants
    - adding equal-split expenses in the event primary currency

Operational constraints:

- AppFunctions delegate into existing event/expense domain logic through `ComponentsMap`.
- The dedicated device-test command is:
  `./gradlew :shared:integration:base:connectedAndroidDeviceTest`
- `shared:integration:base` uses `execution = "HOST"` for its `androidDeviceTest` source set because orchestrator-based test discovery did not report results correctly for this module.

## Deep Links

- The launcher activity is `ru.commonex.ui.MainActivity`.
- Android app links are registered for `http` and `https` URLs on `commonex.ru` with `/event/*` paths.
- The activity uses `launchMode="singleTop"` so new deep-link intents flow into the existing activity instance when applicable.

## Related Docs

- `mobile-sync-and-sharing.md` - share links, universal links, and sync-task behavior
- `ios-app-privacy.md` - privacy consequences of shared Sentry initialization
- `ios-validation-checklist.md` - iOS device validation for startup, links, and sync recovery
