# Project Structure

## Module Structure

```
app/                          # Android application module
shared/                       # Kotlin Multiplatform shared code
  ├── core/                   # Core utilities and infrastructure
  │   ├── ui-design/          # Design system and themes
  │   ├── navigation/         # Navigation components with deep linking
  │   ├── network/            # HTTP client configuration (Ktor + Cronet)
  │   ├── locator/            # Dependency injection container
  │   ├── utils/              # Common utilities
  │   ├── storage-utils/      # Database utilities
  │   ├── ui-utils/           # Compose UI utilities
  │   └── ktor-client-cronet/ # Custom Cronet Engine implementation for Ktor
  ├── feature/                # Feature modules
  │   ├── events/             # Event management (create, join, add participants to existing event or during event creation)
  │   │   └── ui/
  │   │       ├── add_persons/        # Add participants during event creation
  │   │       ├── add_participants/   # Add participants to existing event
  │   │       ├── choose_person/      # Choose current person (participant)
  │   │       ├── create/             # Create new event
  │   │       ├── join/               # Join existing event
  │   │       └── ...
  │   ├── expenses/           # Expense tracking (recording, debts, splits)
  │   ├── settings/           # App settings
  │   ├── menu/               # Navigation menu
  │   ├── share/              # Sharing functionality
  │   └── sync/               # Background sync with WorkManager
  └── integration/            # Platform integration
      ├── base/               # Main navigation host and app setup
      └── databases/          # Room database implementation
iosApp/                       # iOS application (SwiftUI)
baselineprofile/              # Android performance profiling
buildSrc/                     # Build logic and plugins
gradle/                       # Version catalogs and properties
```

## Key Configuration Files

- `gradle/shared.versions.toml` - Shared dependency versions
- `gradle/buildSrc.versions.toml` - Build plugin versions
- `buildSrc/src/main/kotlin/` - Custom Gradle plugins
- `app/proguard-rules.pro` - R8/ProGuard configuration
- `gradle.properties` - Build optimization settings

## Custom Gradle Plugins

- `shared-library-plugin` - Android library module defaults
- `shared-kmm-library-plugin` - KMM module configuration

## Important File Locations

- **Main Activity:** `app/src/main/kotlin/ru/commonex/ui/MainActivity.kt`
- **App Application:** `app/src/main/kotlin/ru/commonex/App.kt`
- **Android AppFunctions entry point:** `shared/integration/base/src/androidMain/kotlin/com/inwords/expenses/integration/base/appfunctions/CommonExAppFunctions.kt`
- **iOS App:** `iosApp/iosApp/iOSApp.swift`
- **Manifest:** `app/src/main/AndroidManifest.xml` (includes deep linking config)
- **ProGuard:** `app/proguard-rules.pro` (minimal rules for Cronet and protobuf)
- **ProGuard:** `app/proguard-rules-autotest.pro` (rules for android tests)
- **ProGuard:** `app/proguard-test-rules.pro` (rules for android tests)

## Version Catalog Structure

- **shared.versions.toml:** Main dependencies (Kotlin, Compose, Room, Ktor, etc.)
- **buildSrc.versions.toml:** Build plugins (AGP, Kotlin compiler)
- Centralized version management prevents conflicts across 50+ modules

## Package Structure

- **Main package:** `ru.commonex` (Android), `com.inwords.expenses` (shared)
- **Namespace pattern:** Feature-based organization (`com.inwords.expenses.feature.{feature-name}`)
- **MainActivity:** `ru.commonex.ui.MainActivity`

## Performance Considerations

- **Baseline profiles:** Module at `baselineprofile/` for Android startup optimization
- **R8 optimization:** Enabled for release builds with custom ProGuard rules
- **Cronet networking:** Using Chrome's network stack for better performance
- **Shared HTTP client lifecycle:** `NetworkComponent` lazily creates and reuses a single Ktor `HttpClient` instance for shared mobile code; avoid bypassing it with ad-hoc client construction in feature modules.
- **Network logging:** Android HTTP logging is enabled only for non-production builds through `NetworkComponentFactory`.

## Code Generation

- **Room database:** Uses KSP for DAO generation (expect redundant visibility warnings)
- **Wire protocol buffers:** Used for settings serialization
- **Compose compiler:** Enabled for all modules with Compose UI
