# iOS App Privacy and App Store Questionnaire

This document reconciles CommonEx iOS app behavior with the privacy policy, Apple privacy manifest requirements, and App Store Connect privacy questionnaire.

## Privacy Policy Alignment

- **Current iOS crash reporting state**: The iOS target initializes Sentry from `iosApp/iosApp/iOSApp.swift`, so the current iOS binary should be treated as collecting Sentry crash diagnostics.
- **Crash reporting toggle**: The app does **not** offer an in-app toggle to disable Sentry on platforms where it is enabled.
- **Analytics**: The app does not ship advertising SDKs or third-party behavioral analytics. Where Sentry is enabled, it is used for crash diagnostics and performance traces only.
- **Future analytics changes**: If you add an analytics SDK later, update `web/public/privacy.html`, this document, the App Store Connect privacy answers, and re-check whether an app-level `PrivacyInfo.xcprivacy` is now required before enabling it in
  production.
- **User content**: Event names, participant names, and expense data are stored locally and synced to our backend; this is core app functionality, not third-party analytics.

## Privacy Manifest (PrivacyInfo.xcprivacy)

- **Sentry SDK**: The Xcode project includes the Sentry Cocoa package through Swift Package Manager (see `iosApp/iosApp.xcodeproj/project.pbxproj`), and recent Sentry Cocoa releases bundle their own `PrivacyInfo.xcprivacy`. Keep verifying the archive privacy
  report so the shipped binary matches the questionnaire answers.
- **App-level manifest**: No app-level `PrivacyInfo.xcprivacy` is currently checked in, and none is required for the current repo behavior. If you add custom iOS data collection or privacy-relevant API usage, add an app-level manifest in `iosApp/iosApp/` and
  declare the data types per [Apple's documentation](https://developer.apple.com/documentation/bundleresources/privacy_manifest_files/describing_data_use_in_privacy_manifests).
- **Verification**: Before submission, archive the app in Xcode, then Control-click the archive → **Generate Privacy Report**. Use this report to confirm all SDKs are covered and to fill App Store Connect privacy details.

## Current Runtime Inputs That Affect Privacy Review

- `iosApp/iosApp/iOSApp.swift` initializes Sentry on app startup for both Debug and Release, with production/development environment selection.
- `shared/integration/base/initializeSentry.kt` sets a non-zero trace sample rate, so performance traces should be treated as enabled alongside crash diagnostics.
- `iosApp/iosApp/iosApp.entitlements` declares `applinks:commonex.ru` for universal links.
- `iosApp/iosApp.xcodeproj/project.pbxproj` currently does not add any other privacy-sensitive third-party SDK package besides Sentry.

## App Store Connect Privacy Questionnaire

When filling the App Privacy section in App Store Connect for the current iOS binary, use these answers based on actual repo behavior and confirm them against the generated Xcode privacy report:

| Question / Data Type        | Answer                                                                                  |
|-----------------------------|-----------------------------------------------------------------------------------------|
| **Crash data**              | Yes - collected via Sentry for app functionality and stability                          |
| **User ID / identifiers**   | No persistent user accounts; event ID and PIN are ephemeral per event                   |
| **Device ID**               | Treat as Yes if the archive privacy report shows Sentry-provided device/app identifiers |
| **User-generated content**  | Yes – event names, participant names, expense data; used for app functionality (sync)   |
| **Advertising / tracking**  | No – no ads, no third-party tracking                                                    |
| **Data linked to identity** | Crash diagnostics may include device-level identifiers; user content is functional data |
| **Data used for tracking**  | No                                                                                      |

Confirm age rating aligns with the privacy policy (16+ for intended audience).
