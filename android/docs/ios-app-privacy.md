# iOS App Privacy and App Store Questionnaire

This document reconciles CommonEx iOS app behavior with the privacy policy, Apple privacy manifest requirements, and App Store Connect privacy questionnaire.

## Privacy Policy Alignment

- **Current iOS crash reporting state**: The iOS target initializes Sentry from `iosApp/iosApp/iOSApp.swift`, so the current iOS binary should be treated as collecting Sentry crash diagnostics.
- **Crash reporting toggle**: The app does **not** offer an in-app toggle to disable Sentry on platforms where it is enabled.
- **Analytics**: The iOS target now initializes AppMetrica for limited product analytics because `shared/core/analytics/src/commonMain/kotlin/com/inwords/expenses/core/analytics/initializeAppMetrica.kt` computes the shared runtime config. The current scope is
  mobile lifecycle/session analytics for product improvement, not ads or cross-app tracking. To avoid sending share-link credentials, the current integration keeps AppMetrica deeplink app-open auto-tracking disabled, so share-link URLs and query
  parameters are not intentionally reported as AppMetrica deeplink-open events. Non-production builds are configured with data sending disabled, location tracking disabled, and revenue auto-tracking disabled.
- **AppMetrica crash collection on iOS**: The current iOS target links `AppMetricaCore` only, not the separate `AppMetricaCrashes` module. That means AppMetrica crash auto-tracking, probably-unhandled crash reporting, and AppMetrica ANR detection are not part of the current iOS binary.
- **Future analytics changes**: If you expand analytics beyond the current AppMetrica lifecycle/session analytics (for example by enabling deeplink app-open tracking, adding in-app deeplink event reporting while the app is already running, custom identified
  user events, or revenue tracking), update `web/public/privacy.html`, `web/public/terms.html`, this document, the App Store Connect privacy answers, and re-check whether an app-level `PrivacyInfo.xcprivacy` is now required before enabling the change
  in production.
- **User content**: Event names, participant names, and expense data are stored locally and synced to our backend; this is core app functionality, not third-party analytics.

## Privacy Manifest (PrivacyInfo.xcprivacy)

- **Third-party SDK manifests**: The Xcode project includes both the Sentry Cocoa and AppMetrica Core packages through Swift Package Manager (see `iosApp/iosApp.xcodeproj/project.pbxproj`). Keep generating the archive privacy report so the shipped binary
  matches the questionnaire answers and so any SDK-manifest changes are caught during upgrades.
- **App-level manifest**: No app-level `PrivacyInfo.xcprivacy` is currently checked in, and none is required for the current repo behavior. If you add custom iOS data collection or privacy-relevant API usage, add an app-level manifest in `iosApp/iosApp/` and
  declare the data types per [Apple's documentation](https://developer.apple.com/documentation/bundleresources/privacy_manifest_files/describing_data_use_in_privacy_manifests).
- **Verification**: Before submission, archive the app in Xcode, then Control-click the archive → **Generate Privacy Report**. Use this report to confirm all SDKs are covered and to fill App Store Connect privacy details.

## Current Runtime Inputs That Affect Privacy Review

- `iosApp/iosApp/iOSApp.swift` initializes Sentry on app startup for both Debug and Release, with production/development environment selection.
- `shared/core/analytics/src/commonMain/kotlin/com/inwords/expenses/core/analytics/initializeAppMetrica.kt` computes the shared AppMetrica runtime config and currently embeds the shared AppMetrica mobile API key.
- `iosApp/iosApp/IOSAppMetricaBridge.swift` provides the Swift `AnalyticsBridge` implementation that talks to the `AppMetricaCore` SDK from app startup. Current config keeps `dataSendingEnabled = false` for non-production builds,
  `appOpenTrackingEnabled = false` so deeplink/universal-link URLs and their query parameters are not intentionally reported to AppMetrica, `locationTracking = false`, `revenueAutoTrackingEnabled = false`, and enables SDK logs only in non-production
  builds.
- Equivalent iOS AppMetrica crash controls exist under `AppMetricaCrashesConfiguration` (`autoCrashTracking`, `probablyUnhandledCrashReporting`, and `applicationNotRespondingDetection`), but they are not reachable in the current app because the target does not link the `AppMetricaCrashes` product.
- `shared/core/observability/src/commonMain/kotlin/com/inwords/expenses/core/observability/initializeSentry.kt` sets a non-zero trace sample rate, so performance traces should be treated as enabled alongside crash diagnostics.
- `iosApp/iosApp/iosApp.entitlements` declares `applinks:commonex.ru` for universal links.
- `iosApp/iosApp.xcodeproj/project.pbxproj` now includes both Sentry and AppMetrica Core Swift packages.

## App Store Connect Privacy Questionnaire

When filling the App Privacy section in App Store Connect for the current iOS binary, use these answers based on actual repo behavior and confirm them against the generated Xcode privacy report:

| Question / Data Type            | Answer                                                                                                 |
|---------------------------------|--------------------------------------------------------------------------------------------------------|
| **Crash data**                  | Yes - collected via Sentry for app functionality and stability                                         |
| **Product interaction / usage** | Yes - limited mobile lifecycle/session analytics via AppMetrica for product improvement                |
| **User ID / identifiers**       | No persistent user accounts today; analytics stays anonymous unless future identified events are added |
| **Device ID**                   | Treat as Yes if the archive privacy report shows AppMetrica- or Sentry-provided device/app identifiers |
| **User-generated content**      | Yes – event names, participant names, expense data; used for app functionality (sync)                  |
| **Advertising / tracking**      | No – no ads, no cross-context third-party tracking                                                     |
| **Data linked to identity**     | Crash diagnostics and analytics may include device-level identifiers; user content is functional data  |
| **Data used for tracking**      | No                                                                                                     |

Confirm age rating aligns with the privacy policy (16+ for intended audience).
