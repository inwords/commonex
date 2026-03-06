
-dontobfuscate # Keep class names for easier debugging and test assertions
-dontshrink # It's too difficult to enable shrinking

# Suppress optional Chromium/management classes R8 complains about during minified autotest build
-dontwarn internal.org.chromium.build.NativeLibraries
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean
-dontwarn org.chromium.base.FeatureList
-dontwarn org.chromium.base.FeatureMap
-dontwarn org.chromium.base.FeatureOverrides
-dontwarn org.chromium.base.FeatureParam
-dontwarn org.chromium.base.version_info.VersionConstantsBridgeJni

# Suppress warnings for optional AndroidX Window extension classes that may not be available during ASM instrumentation
-dontwarn androidx.window.extensions.embedding.**
-dontwarn androidx.window.sidecar.**
-dontwarn androidx.compose.animation.tooling.ComposeAnimatedProperty
-dontwarn javax.lang.model.element.Modifier
