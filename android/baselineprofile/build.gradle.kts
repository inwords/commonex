import com.android.build.api.dsl.ManagedVirtualDevice
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(buildSrc.plugins.android.test)
    alias(shared.plugins.androidx.baselineprofile)
    // https://github.com/androidx/androidx/blob/22f430ac409089812ce985bfa303fccff93cd095/benchmark/baseline-profile-gradle-plugin/src/main/kotlin/androidx/baselineprofile/gradle/apptarget/BaselineProfileAppTargetPlugin.kt#L48
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            "-Xreturn-value-checker=check",
            "-Xexplicit-backing-fields",
        )
        extraWarnings.set(true)
    }
}

android {
    namespace = "ru.commonex.baselineprofile"
    compileSdk = 36

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        minSdk = 28
        targetSdk = 36

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"

    // This code creates the gradle managed device used to generate baseline profiles.
    // To use GMD please invoke generation through the command line:
    // ./gradlew :app:generateBaselineProfile
    testOptions {
        @Suppress("UnstableApiUsage")
        managedDevices.allDevices {
            create<ManagedVirtualDevice>("pixel6Api34") {
                device = "Pixel 6"
                apiLevel = 34
                systemImageSource = "google"
            }
        }
    }
}

// This is the configuration block for the Baseline Profile plugin.
// You can specify to run the generators on a managed devices or connected devices.
baselineProfile {
    managedDevices += "pixel6Api34"
    useConnectedDevices = false
}

dependencies {
    implementation(shared.androidx.test.runner)
    implementation(shared.androidx.test.ext.junit)
    implementation(shared.androidx.test.uiautomator)
    implementation(shared.androidx.test.benchmark.macro.junit4)
}

androidComponents {
    onVariants { v ->
        val artifactsLoader = v.artifacts.getBuiltArtifactsLoader()
        @Suppress("UnstableApiUsage")
        v.instrumentationRunnerArguments.put(
            "targetAppId",
            v.testedApks.map { artifactsLoader.load(it)?.applicationId!! }
        )
    }
}
