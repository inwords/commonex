---
name: update-mobile-dependencies
description: Use when updating mobile (Android/iOS) or KMM dependency versions, Gradle toolchain versions, or related mobile build inputs in the CommonEx android KMM project.
---

# Update Mobile Dependencies

Follow `.agents/skills/update-dependency-batch`. This skill adds mobile (KMM) discovery, bundles, and validation.

## Version sources

- `android/gradle/shared.versions.toml`
- `android/gradle/buildSrc.versions.toml`
- `android/gradle/wrapper/gradle-wrapper.properties`
- `android/gradle/gradle-daemon-jvm.properties`
- `android/build.gradle.kts`
- `android/settings.gradle.kts`

Discover updates:

```bash
./gradlew --quiet dependencyUpdates --refresh-dependencies -Drevision=release --no-parallel
```

Read `android/build/dependencyUpdates/report.txt`.

On Windows, from `android/`: `.\gradlew.bat` with quoted `-D` properties.

## Official sources

Use URLs in the catalog comments plus AndroidX, Kotlin, KSP, Ktor, CMP, Wire, Sentry, Gradle/AGP release pages, and compatibility matrices when splitting bundles.

## Bundle boundaries

Move together unless docs prove a smaller split is safe:

- **Kotlin toolchain** — Kotlin, Kotlin Gradle plugin, Kotlin test, Compose compiler, serialization plugin, KSP if required
- **Android build toolchain** — AGP, Gradle wrapper, daemon JDK, CI JDK if needed
- **Compose UI stack** — Compose BOM, CMP, CMP Material3, Compose UI test libs, Navigation3 when coupled
- **Room stack** — Room, bundled SQLite, KSP if required
- **Ktor stack** — all `ktor` artifacts
- **Sentry stack** — KMP + Android Gradle plugins and nested SDK changelogs they reference
- **Benchmark/profile** — benchmark and baseline-profile plugins and related libs

Standalone libraries may be separate bundles.

## Proposal

Use the parent skill’s **one section per bundle** format: `## N — …`, version/files/source lines, **Summary**, then the full numbered changelog. No separate menu vs notes sections.

### Mobile relevance markers (numbered changelog only)

In the **full numbered changelog** (not in **Summary**), append `📱` at the **end** of each line or bullet that is relevant to this repo’s **mobile** surface (Android and/or iOS).

Mark a point when it clearly applies to one or more of:

- **Android** — app / `androidMain`, manifests, permissions, Android-specific APIs; AGP; Android SDK levels; lint; R8/ProGuard; APK/AAB; signing; Android build/test tasks (`lintDebug`, `assembleDebug`, instrumented UI tests); AndroidX / Jetpack / `android` / `androidx` / `*-android` artifacts
- **iOS** — app / `iosMain` / `apple` targets; UIKit / SwiftUI; Xcode; CocoaPods / SPM; IPA/TestFlight; iOS simulator or device fixes; artifacts named `ios`, `apple`, or `*-ios`
- **Mobile KMP** — `commonMain` changes with stated Android or iOS impact; mobile-only or mobile-first fixes in CMP/Ktor/Sentry/etc. release notes
- **Nested mobile SDKs** — bundled Android Gradle / AndroidX / iOS / Cocoa changelogs when part of a mobile stack bundle (e.g. Sentry KMP + native mobile plugins)

Do **not** mark points that are only about desktop, JS/Wasm, web, server/JVM-only, or generic shared KMP/common code with no stated Android or iOS impact.

When unsure, leave the line unmarked; do not guess.

Example:

```markdown
1. Shared Ktor client API change for all platforms.
2. Fix crash on Android 14 when restoring process death. 📱
3. Fix VoiceOver focus order on iOS 18. 📱
```

Also list **already on latest** and **excluded** (e.g. `guava` JRE variant; CMP Material3 SNAPSHOT from the versions plugin — use the version from the [CMP release table](https://github.com/JetBrains/compose-multiplatform/releases) for the target CMP, such as `1.11.0-alpha07` with CMP `1.11.0`).

Do not hide pre-releases on a line the repo already uses; do not suggest lower-tier pre-releases.

**Stop** after the proposal. Do not edit catalogs until the user selects bundles.

## Apply (after user selection)

- Update exact version keys or wrapper URL
- Propagate refactors from release notes; fix local deprecations when reasonable
- Align docs/config if Compose, Kotlin, AGP, or Room require it

## Validation (after apply)

Follow project's validation instructions: from `android/`, always `--quiet`.

If upstream guidance conflicts with repo docs, flag it instead of picking silently.
