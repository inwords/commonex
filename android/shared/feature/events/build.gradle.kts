import com.inwords.expenses.plugins.SharedKmmLibraryPlugin.Companion.applyKmmDefaults

plugins {
    id("shared-kmm-library-plugin")
    alias(shared.plugins.kotlin.serialization)
    alias(shared.plugins.compose.compiler)
    alias(shared.plugins.compose.multiplatform.compiler)
}

kotlin {
    android {
        namespace = "com.inwords.expenses.feature.events"

        @Suppress("UnstableApiUsage")
        optimization {
            consumerKeepRules.files.add(file("consumer-rules.pro"))
        }

        experimentalProperties["android.experimental.kmp.enableAndroidResources"] = true

        withHostTest {}
    }

    applyKmmDefaults("sharedEvents")

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":shared:core:utils"))
                implementation(project(":shared:core:observability"))
                implementation(project(":shared:core:ui-utils"))
                implementation(project(":shared:core:ui-design"))
                implementation(project(":shared:core:storage-utils"))
                implementation(project(":shared:core:navigation"))
                implementation(project(":shared:core:network"))
                implementation(project(":shared:feature:settings"))

                implementation(shared.coroutines.core)

                implementation(shared.kotlinx.collections.immutable)

                implementation(shared.kotlinx.serialization.json)
                implementation(shared.kotlinx.datetime)
                implementation(shared.ionspin.kotlin.bignum)

                implementation(shared.lifecycle.runtime.compose.multiplatform)
                implementation(shared.lifecycle.viewmodel.compose.multiplatform)
                implementation(shared.lifecycle.viewmodel.navigation3.multiplatform)

                implementation(shared.room.runtime)

                implementation(shared.ktor.client.core)

                implementation(shared.compose.ui.multiplatform)
                implementation(shared.compose.material3.multiplatform)
                implementation(shared.compose.ui.tooling.preview.multiplatform)
                implementation(shared.compose.components.resources.multiplatform)

                implementation(shared.navigation3.ui.multiplatform)
                implementation(shared.compose.material.icons.core)
            }
        }

        androidMain {
            dependencies {
                implementation(shared.compose.ui.tooling)
            }
        }
        commonTest {
            dependencies {
                implementation(shared.kotlin.test)
                implementation(shared.coroutines.test)
                implementation(shared.ktor.client.mock)
                implementation(shared.ktor.client.content.negotiation)
                implementation(shared.ktor.serialization.kotlinx.json)
            }
        }
    }

    compilerOptions {
        // Common compiler options applied to all Kotlin source sets
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}

compose.resources {
    // FIXME: use textFixtures ScreenObjects for tests
    publicResClass = true
}

dependencies {
    add("androidHostTestImplementation", shared.kotlin.test)
    add("androidHostTestImplementation", shared.coroutines.test)
    add("androidHostTestImplementation", shared.junit.jupiter.api)
    add("androidHostTestImplementation", shared.mockk)
    add("androidHostTestImplementation", shared.turbine)
}
