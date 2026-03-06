import com.android.build.api.dsl.ManagedVirtualDevice
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(buildSrc.plugins.android.application)
    alias(shared.plugins.compose.compiler)
    alias(shared.plugins.android.junit5)
    alias(shared.plugins.sentry.android.gradle)
    alias(shared.plugins.androidx.baselineprofile)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)

        freeCompilerArgs.addAll(
            "-Xcontext-parameters",
            "-Xdata-flow-based-exhaustiveness",
            "-Xreturn-value-checker=check",
            "-Xexplicit-backing-fields",
        )
        extraWarnings.set(true)
    }
}

android {
    namespace = "ru.commonex"
    compileSdk = 36

    defaultConfig {
        applicationId = "ru.commonex"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "2026.02.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["clearPackageData"] = "true"

        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        create("release") {
            storeFile = file("keystore.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (System.getenv("CI") == "true") {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        @Suppress("UNUSED")
        val autotest by creating {
            initWith(getByName("release"))
            isShrinkResources = false
            proguardFile("proguard-rules-autotest.pro")
            testProguardFile("proguard-test-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        animationsDisabled = true
        execution = "ANDROIDX_TEST_ORCHESTRATOR"

        @Suppress("UnstableApiUsage")
        managedDevices {
            allDevices {
                create<ManagedVirtualDevice>("pixel6Api35Atd") {
                    device = "Pixel 6"
                    apiLevel = 35
                    systemImageSource = "aosp-atd"
                    testedAbi = "x86_64"
                }
            }
        }
    }
    testBuildType = "autotest"

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":shared:core:utils"))
    implementation(project(":shared:core:ui-design"))
    implementation(project(":shared:integration:base"))

    // network
    implementation(shared.cronet.embedded)

    // guava (can't be added as `autotest` in integration-base)
    add("autotestRuntimeOnly", shared.guava)

    // compose
    val composeBom = platform(shared.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(shared.compose.ui)
    implementation(shared.compose.material3)
    implementation(shared.compose.ui.tooling)

    // misc androidx stuff
    implementation(shared.core.ktx)
    implementation(shared.activity.compose)

    implementation(shared.work.runtime.ktx)

    implementation(shared.profileinstaller)

    testImplementation(shared.junit.jupiter.api)
    testRuntimeOnly(shared.junit.jupiter.engine)

    androidTestImplementation(shared.androidx.test.ext.junit)
    androidTestImplementation(shared.androidx.test.runner)
    androidTestImplementation(shared.androidx.test.espresso.core)
    androidTestUtil(shared.androidx.test.orchestrator)
    androidTestImplementation(shared.androidx.compose.ui.test.android)
    androidTestImplementation(shared.androidx.compose.ui.test.junit4)

    androidTestImplementation(project(":shared:feature:events")) // FIXME: use textFixtures ScreenObjects for tests
    androidTestImplementation(project(":shared:feature:expenses"))
    androidTestImplementation(project(":shared:feature:menu"))
    androidTestImplementation(shared.compose.components.resources.multiplatform)

    baselineProfile(project(":baselineprofile"))
}

androidComponents {
    beforeVariants(selector().withName("^(benchmarkAutotest|nonMinifiedAutotest)$".toPattern())) { variantBuilder ->
        variantBuilder.enable = false
    }
}

sentry {
    org.set("inwords")
    projectName.set("commonex")

    authToken.set(System.getenv("SENTRY_AUTH_TOKEN"))

    val uploadMapping = System.getenv("CI") == "true"
    autoUploadProguardMapping.set(uploadMapping)

    tracingInstrumentation {
        enabled.set(true)

        features.set(emptySet())
    }

    autoInstallation {
        enabled.set(false)
    }

    includeDependenciesReport = false

    telemetry.set(false)
}

baselineProfile {
    dexLayoutOptimization = true

    warnings {
        disabledVariants = false
    }
}
