# iOS App Privacy and App Store Questionnaire

This document reconciles CommonEx iOS app behavior with the privacy policy, Apple privacy manifest requirements, and App Store Connect privacy questionnaire.

## Privacy Policy Alignment

- **Crash reporting toggle**: The app does **not** offer an in-app toggle to disable Sentry crash reporting. The privacy policy has been updated to state this explicitly and to direct users to contact us if they wish to opt out.
- **Analytics**: Sentry collects crash logs and diagnostics only; no marketing analytics or advertising SDKs.
- **User content**: Event names, participant names, and expense data are stored locally and synced to our backend; this is core app functionality, not third-party analytics.

## Privacy Manifest (PrivacyInfo.xcprivacy)

- **Sentry SDK**: The Sentry Cocoa SDK (v8.21+) bundles its own `PrivacyInfo.xcprivacy` declaring crash data collection. No app-level manifest is required to cover Sentry's collection.
- **App-level manifest**: The app does not directly use privacy-relevant APIs beyond what Sentry uses. If you add custom analytics or new data collection, add an app-level `PrivacyInfo.xcprivacy` in `iosApp/iosApp/` and declare the data types per [Apple's documentation](https://developer.apple.com/documentation/bundleresources/privacy_manifest_files/describing_data_use_in_privacy_manifests).
- **Verification**: Before submission, archive the app in Xcode, then Control-click the archive → **Generate Privacy Report**. Use this report to confirm all SDKs are covered and to fill App Store Connect privacy details.

## App Store Connect Privacy Questionnaire

When filling the App Privacy section in App Store Connect, use these answers based on actual app behavior:

| Question / Data Type        | Answer                                                                                |
|-----------------------------|---------------------------------------------------------------------------------------|
| **Crash data**              | Yes – collected via Sentry for app functionality (stability)                          |
| **User ID / identifiers**   | No persistent user accounts; event ID and PIN are ephemeral per event                 |
| **Device ID**               | May be included in crash reports (Sentry); not used for tracking                      |
| **User-generated content**  | Yes – event names, participant names, expense data; used for app functionality (sync) |
| **Advertising / tracking**  | No – no ads, no third-party tracking                                                  |
| **Data linked to identity** | Crash data may include device info; not linked to named user identity                 |
| **Data used for tracking**  | No                                                                                    |

Confirm age rating aligns with the privacy policy (16+ for intended audience).
