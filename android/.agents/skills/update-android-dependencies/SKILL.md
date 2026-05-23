---
name: update-android-dependencies
description: Use when updating Android or KMM dependency versions, Gradle toolchain versions, or related mobile containerized build inputs in the CommonEx android project.
---

# Update Android Dependencies

## Version Sources In Repo

Read versions from:
- `android/gradle/shared.versions.toml`
- `android/gradle/buildSrc.versions.toml`
- `android/gradle/wrapper/gradle-wrapper.properties`
- `android/gradle/gradle-daemon-jvm.properties`
- `android/build.gradle.kts`
- `android/settings.gradle.kts`

Use the existing repo command to discover updates:

```bash
./gradlew --quiet dependencyUpdates --refresh-dependencies -Drevision=release --no-parallel
```

Read the report from `android/build/dependencyUpdates/report.txt`.

## Where To Check On The Internet

Use official sources first:
- URLs already embedded as comments in `shared.versions.toml`, `buildSrc.versions.toml`, and `settings.gradle.kts`
- AndroidX release pages for Room, Compose, WorkManager, Activity, Benchmark, Profile Installer, AppFunctions, and test libraries
- Kotlin, KSP, kotlinx, Ktor, Compose Multiplatform, Wire, MockK, and Sentry official changelogs or release pages
- Gradle and Android Gradle Plugin release notes
- `actions/setup-java` supported-distributions docs when the daemon JDK or CI JDK may need to move

If a library family has a compatibility matrix, use it before proposing split updates.

## Smallest Valid Update Bundles

Use these bundle boundaries unless upstream docs prove a smaller split is safe:
- **Kotlin toolchain**: Kotlin, Kotlin Gradle plugin, Kotlin test, Compose compiler plugin, Kotlin serialization plugin, KSP if required by compatibility
- **Android build toolchain**: AGP, Gradle wrapper, daemon JDK, and CI JDK references if compatibility requires them
- **Compose UI stack**: Compose BOM, Compose Multiplatform, Compose Material3 Multiplatform, Compose UI test libs, Activity Compose, lifecycle multiplatform, Navigation3 if compatibility docs require it
- **Room stack**: Room runtime/compiler/testing, bundled SQLite, KSP if Room or KSP compatibility requires it
- **Ktor stack**: all Ktor client and serialization artifacts
- **Sentry stack**: Sentry Kotlin Multiplatform plugin, Sentry Android Gradle plugin, and recursively discovered dependent SDK notes
- **Benchmark/profile stack**: AndroidX benchmark and baseline profile plugins plus related benchmark libraries

Standalone libraries may be proposed separately when there is no compatibility coupling.

## Proposal Rules

Before editing, present numbered bundles with:
- current and target versions
- affected catalog keys or files
- why the bundle must move together
- concise release-note impact
- all source links

Do not hide pre-release candidates when the repo already uses pre-release for that same line. Do not suggest lower-tier pre-releases.

## Apply Rules

When the user selects bundles:
- update the exact version keys or wrapper URL
- propagate any required code refactors from release notes
- remove deprecations in touched areas when reasonably local
- if Compose, Kotlin, AGP, or Room changes require doc or config alignment, update the related repo files instead of leaving drift

## Validation

Run from `android/` and always keep `--quiet`:

```bash
./gradlew --quiet testHostTest
./gradlew --quiet lintDebug
```

Escalate as needed:
- `./gradlew --quiet assembleDebug`
- `./gradlew --quiet test`
- `./gradlew --quiet assembleRelease`
- project- or module-specific compile or host-test tasks when only one surface was updated

If upstream migration guidance conflicts with repo docs, flag it instead of silently choosing one.
