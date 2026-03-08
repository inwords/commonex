# iOS app versioning

iOS uses Xcode-native versioning in `iosApp/iosApp.xcodeproj/project.pbxproj`:

- **MARKETING_VERSION** — user-visible version (e.g. `2026.02.1`). Shown as CFBundleShortVersionString.
- **CURRENT_PROJECT_VERSION** — build number (integer, e.g. `5`). Shown as CFBundleVersion. At runtime the plist value is a string; in KMM/iOS code read it as `(info["CFBundleVersion"] as? String)?.toIntOrNull() ?: 0`.

**Convention:** Keep these aligned with the Android app when releasing:

- Set `MARKETING_VERSION` to the same value as `versionName` in `app/build.gradle.kts`.
- Set `CURRENT_PROJECT_VERSION` to the same value as `versionCode` in `app/build.gradle.kts`.

Update both in the Xcode project (Debug and Release configurations) whenever you bump the Android app version for a release.
