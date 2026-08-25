# Troubleshooting

## Build Warnings and Issues

### Expected Warnings (safe to ignore)

- `Calculating task graph as configuration cache cannot be reused because file 'gradle\buildSrc.versions.toml' has changed`
- Cronet namespace warnings in manifest merger
- Redundant visibility modifier warnings from generated Room code
- Native library stripping warnings for specific .so files
- `OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes` during unit tests
- Isolated Projects incubating-feature notices from Gradle

### Build Process Notes

- Configuration cache and Isolated Projects are enabled; project configuration may run in parallel
- First build after clean takes ~28 seconds
- Incremental builds are much faster due to Gradle caching
- KSP generates code for Room DAOs and may show redundant modifier warnings
- Dependency updates command may take 5+ minutes and should not be interrupted
- PowerShell users: Use `;` instead of `&&` for command chaining
- PowerShell users: Quote `-D...` Gradle properties (for example `"-Dcom.android.tools.r8.disableApiModeling=true"`) to avoid accidental task parsing

## Common Issues and Solutions

- **Build fails after dependency changes:** Run `./gradlew --quiet clean` first
- **KSP errors:** Usually resolved by clean build
- **Version conflicts:** Check `gradle/shared.versions.toml` for centralized versions

## Build Issues

- **Clean builds solve most KSP issues:** `./gradlew --quiet clean`
- **Memory problems:** Check JVM args in gradle.properties (currently set to 2GB)
- **Isolated Projects issues:** Run `./gradlew --quiet help --isolated-projects -Dorg.gradle.isolated-projects.diagnostics=true` to collect constraint violations
- **Configuration cache:** Can be cleared by deleting `.gradle/configuration-cache/`

## Runtime Issues

- **Network:** Uses Cronet embedded, check manifest for network permissions
- **Database:** Room migrations handled automatically, check for schema changes
- **Deep linking:** App handles commonex.ru domain, test with intent filters
- **Background sync:** WorkManager requires proper initialization
- **Expense details exchange rate crash (`Negative decimal precision is not allowed`):** Avoid formatting computed rates via `BigDecimal.scale(...)`-based helpers. Use `DecimalMode` division + `roundToDigitPositionAfterDecimalPoint(2, ...)` and fixed-scale
  numeric formatting. See `android/docs/patterns.md` (`Numeric BigDecimal Patterns`).

## Performance Debugging

- **Baseline profiles:** Generated in `baselineprofile/` module for startup optimization
- **R8 optimization:** Check `app/src/main/keepRules/` and run `./gradlew --quiet :app:analyzeReleaseR8Config`; the standalone report is written to `app/build/reports/r8/r8-config-analyzer-release.html`
- **Build times:** First build ~28s, incremental much faster with Gradle cache

## Known TODOs and Technical Debt

- Several "TODO mvp" comments indicate MVP-level implementations that need improvement
- Some String vs Double type inconsistencies in network DTOs
- User agent configuration needs finalization in HTTP client

## Environment Setup

### Required Tools

- **JDK:** Same as CI (see `.github/workflows/android.yml`). Project targets JVM 17.
- **Android SDK:** API level as in `app/build.gradle.kts`. **Gradle:** use wrapper (version in `gradle/wrapper/gradle-wrapper.properties`), do not install separately.
- **Git** for version control. Full list: [`local-agent-prerequisites.md`](local-agent-prerequisites.md).

### IDE Configuration

- **Android Studio** recommended for Android and KMM development
- Enable Kotlin Multiplatform plugin
- Configure JAVA_HOME to match the JDK used in CI (see workflow file)
