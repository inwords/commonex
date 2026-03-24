# iOS App Privacy and App Store Questionnaire

This document reconciles CommonEx iOS app behavior with the privacy policy, Apple privacy manifest requirements, and App Store Connect privacy questionnaire.

## Privacy Policy Alignment

- **Current iOS crash reporting state**: The iOS target initializes Sentry from `iosApp/iosApp/iOSApp.swift`, so the current iOS binary should be treated as collecting Sentry crash diagnostics.
- **Crash reporting toggle**: The app does **not** offer an in-app toggle to disable Sentry on platforms where it is enabled.
- **Analytics**: The iOS target now initializes PostHog for limited product analytics because the embedded PostHog project token in `shared/core/analytics/src/commonMain/kotlin/com/inwords/expenses/core/analytics/PostHogProjectConfig.kt` is configured. The current scope is mobile lifecycle analytics only (for example install/open/update/background) for product improvement, not ads or cross-app tracking. Non-production builds are opted out, and the current PostHog host is the EU cloud (`https://eu.i.posthog.com`).
- **Future analytics changes**: If you expand analytics beyond the current PostHog lifecycle events (for example custom identified user events), update `web/public/privacy.html`, `web/public/terms.html`, this document, the App Store Connect privacy answers, and re-check whether an app-level `PrivacyInfo.xcprivacy` is now required before enabling the change in production.
- **User content**: Event names, participant names, and expense data are stored locally and synced to our backend; this is core app functionality, not third-party analytics.

## Privacy Manifest (PrivacyInfo.xcprivacy)

- **Third-party SDK manifests**: The Xcode project includes both the Sentry Cocoa and PostHog iOS packages through Swift Package Manager (see `iosApp/iosApp.xcodeproj/project.pbxproj`). Keep generating the archive privacy report so the shipped binary matches the questionnaire answers and so any SDK-manifest changes are caught during upgrades.
- **App-level manifest**: No app-level `PrivacyInfo.xcprivacy` is currently checked in, and none is required for the current repo behavior. If you add custom iOS data collection or privacy-relevant API usage, add an app-level manifest in `iosApp/iosApp/` and
  declare the data types per [Apple's documentation](https://developer.apple.com/documentation/bundleresources/privacy_manifest_files/describing_data_use_in_privacy_manifests).
- **Verification**: Before submission, archive the app in Xcode, then Control-click the archive → **Generate Privacy Report**. Use this report to confirm all SDKs are covered and to fill App Store Connect privacy details.

## Current Runtime Inputs That Affect Privacy Review

- `iosApp/iosApp/iOSApp.swift` initializes Sentry on app startup for both Debug and Release, with production/development environment selection.
- `shared/core/analytics/src/commonMain/kotlin/com/inwords/expenses/core/analytics/initializePostHog.kt` computes the shared PostHog runtime config.
- `shared/core/analytics/src/commonMain/kotlin/com/inwords/expenses/core/analytics/PostHogProjectConfig.kt` supplies the embedded PostHog token/host, currently pointing at the EU cloud.
- `iosApp/iosApp/IOSPostHogBridge.swift` provides the Swift `PostHogBridge` implementation that talks to the `posthog-ios` SDK from app startup. Current config keeps `captureApplicationLifecycleEvents = true`, `captureScreenViews = false`, `enableSwizzling = false`, and `optOut = true` for non-production builds.
- `shared/core/observability/src/commonMain/kotlin/com/inwords/expenses/core/observability/initializeSentry.kt` sets a non-zero trace sample rate, so performance traces should be treated as enabled alongside crash diagnostics.
- `iosApp/iosApp/iosApp.entitlements` declares `applinks:commonex.ru` for universal links.
- `iosApp/iosApp.xcodeproj/project.pbxproj` now includes both Sentry and PostHog Swift packages.

## App Store Connect Privacy Questionnaire

When filling the App Privacy section in App Store Connect for the current iOS binary, use these answers based on actual repo behavior and confirm them against the generated Xcode privacy report:

| Question / Data Type            | Answer                                                                                                                    |
|---------------------------------|---------------------------------------------------------------------------------------------------------------------------|
| **Crash data**                  | Yes - collected via Sentry for app functionality and stability                                                            |
| **Product interaction / usage** | Yes - limited mobile lifecycle analytics via PostHog (install/open/update/background) for product improvement            |
| **User ID / identifiers**       | No persistent user accounts today; analytics stays anonymous unless future identified events are added                    |
| **Device ID**                   | Treat as Yes if the archive privacy report shows PostHog- or Sentry-provided device/app identifiers                      |
| **User-generated content**      | Yes – event names, participant names, expense data; used for app functionality (sync)                                    |
| **Advertising / tracking**      | No – no ads, no cross-context third-party tracking                                                                        |
| **Data linked to identity**     | Crash diagnostics and analytics may include device-level identifiers; user content is functional data                    |
| **Data used for tracking**      | No                                                                                                                        |

Confirm age rating aligns with the privacy policy (16+ for intended audience).
