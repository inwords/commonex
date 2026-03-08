# iOS Validation Checklist

Use this checklist to validate the iOS app before TestFlight or App Store submission. Run on a macOS machine with Xcode.

## Prerequisites

- macOS with Xcode 16+
- Gradle wrapper (run from `android/` directory)
- For device tests: physical iPhone connected

## 1. Simulator Build

From repository root:

```bash
cd android/iosApp
xcodebuild -scheme iosApp -destination 'platform=iOS Simulator,name=iPhone 16 Pro Max,OS=18.5' build
```

Or via Gradle from `android/`:

```bash
./gradlew :shared:integration:base:linkDebugFrameworkIosSimulatorArm64
```

## 2. Release Archive Build

From repository root:

```bash
cd android/iosApp
xcodebuild -scheme iosApp -configuration Release -destination generic/platform=iOS archive
```

Archive output appears in Xcode Organizer. Use this archive for TestFlight upload.

## 3. KMM iOS Targets Verification

The KMM plugin defines **iosArm64** and **iosSimulatorArm64** only. To verify shared code compiles for iOS:

```bash
cd android
./gradlew :shared:integration:base:linkDebugFrameworkIosSimulatorArm64
```

There are currently no checked-in `src/iosTest` or `src/iosSimulatorArm64Test` sources in this repo, so CI remains build-only unless an iOS test target is added later. If you introduce iOS tests, use this simulator command:

```bash
./gradlew iosSimulatorArm64Test
```

Do not use `./gradlew iosX64Test`; that target is not configured in this project.

Until then, the link task above is the repo's automated KMM iOS compilation check.

## 4. Manual Device Checks

On a physical device, verify:

- [ ] Install and open the app
- [ ] Create an event
- [ ] Share event (generate link, copy to clipboard)
- [ ] Open universal link from Messages, Notes, or Safari
- [ ] Kill and relaunch after offline edits; verify sync recovery
- [ ] Confirm app icon, display name (CommonEx), version/build, and legal links (privacy, terms)

## 5. Pre-Submission Verification

- [ ] Generate Privacy Report: Xcode Organizer → Control-click archive → Generate Privacy Report
- [ ] Confirm App Store Connect privacy questionnaire answers (see `ios-app-privacy.md`)
- [ ] Verify version/build aligned with Android (see `ios-versioning.md`)

## CI Reference

The GitHub workflow `android.yml` runs `build_ios` on `macos-latest` with:

```yaml
xcodebuild -scheme iosApp -destination 'platform=iOS Simulator,name=iPhone 16 Pro Max,OS=18.5' build
```

This validates simulator build only. Archive and device checks must be done manually before submission.
