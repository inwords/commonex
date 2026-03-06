import com.inwords.expenses.plugins.SharedKmmLibraryPlugin.Companion.applyKmmDefaults

plugins {
    id("shared-kmm-library-plugin")
    alias(shared.plugins.compose.compiler)
    alias(shared.plugins.compose.multiplatform.compiler)
    alias(shared.plugins.sentry.kotlin.multiplatform)
    alias(shared.plugins.ksp)
}

kotlin {
    android {
        namespace = "com.inwords.expenses.integration.base"

        @Suppress("UnstableApiUsage")
        optimization {
            consumerKeepRules.files.add(file("consumer-rules.pro"))
        }

        androidResources {
            enable = true
        }

        withDeviceTest {
            animationsDisabled = true
            // AppFunctions classes in this module break orchestrator-based test discovery on device.
            execution = "HOST"
        }
    }

    applyKmmDefaults("sharedIntegrationBase")

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":shared:core:utils"))
                implementation(project(":shared:core:locator"))
                implementation(project(":shared:core:storage-utils"))
                api(project(":shared:core:navigation"))
                implementation(project(":shared:core:network"))
                implementation(project(":shared:feature:events"))
                implementation(project(":shared:feature:expenses"))
                implementation(project(":shared:feature:sync"))
                implementation(project(":shared:feature:share"))
                implementation(project(":shared:feature:settings"))
                implementation(project(":shared:feature:menu"))
                implementation(project(":shared:integration:databases"))

                implementation(shared.coroutines.core)

                implementation(shared.ktor.client.core)

                implementation(shared.lifecycle.runtime.compose.multiplatform)

                implementation(shared.compose.ui.multiplatform)
                implementation(shared.compose.material3.multiplatform)
                implementation(shared.compose.ui.tooling.preview.multiplatform)

                api(shared.navigation3.ui.multiplatform)
                implementation(shared.lifecycle.viewmodel.navigation3.multiplatform)

                implementation(shared.kotlinx.atomicfu)
            }
        }
        androidMain {
            dependencies {
                implementation(shared.androidx.appfunctions)
                implementation(shared.androidx.appfunctions.service)
                implementation(shared.ionspin.kotlin.bignum)
            }
        }
        @Suppress("unused")
        val androidDeviceTest by getting {
            dependencies {
                implementation(shared.androidx.test.runner)
                implementation(shared.androidx.test.ext.junit)
                implementation(shared.mockk.android)
            }
        }
    }

    compilerOptions {
        // Common compiler options applied to all Kotlin source sets
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}

dependencies {
    kspAndroid(shared.androidx.appfunctions.compiler)
}
