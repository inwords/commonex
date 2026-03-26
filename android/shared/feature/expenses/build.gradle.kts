import com.inwords.expenses.plugins.SharedKmmLibraryPlugin.Companion.applyKmmDefaults

plugins {
    id("shared-kmm-library-plugin")
    alias(shared.plugins.kotlin.serialization)
    alias(shared.plugins.compose.compiler)
    alias(shared.plugins.compose.multiplatform.compiler)
}

kotlin {
    android {
        namespace = "com.inwords.expenses.feature.expenses"

        @Suppress("UnstableApiUsage")
        optimization {
            consumerKeepRules.files.add(file("consumer-rules.pro"))
        }

        withHostTest {}

        experimentalProperties["android.experimental.kmp.enableAndroidResources"] = true
    }

    applyKmmDefaults("sharedExpenses")

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":shared:core:utils"))
                implementation(project(":shared:core:observability"))
                implementation(project(":shared:core:storage-utils"))
                implementation(project(":shared:core:ui-utils"))
                implementation(project(":shared:core:ui-design"))
                implementation(project(":shared:core:navigation"))
                implementation(project(":shared:core:network"))
                implementation(project(":shared:feature:events"))
                implementation(project(":shared:feature:menu"))
                implementation(project(":shared:feature:settings"))

                implementation(shared.annotation)

                implementation(shared.coroutines.core)

                implementation(shared.kotlinx.serialization.json)
                implementation(shared.kotlinx.datetime)
                implementation(shared.kotlinx.collections.immutable)

                implementation(shared.lifecycle.runtime.compose.multiplatform)
                implementation(shared.lifecycle.viewmodel.compose.multiplatform)

                implementation(shared.room.runtime)

                implementation(shared.ktor.client.core)

                implementation(shared.compose.ui.multiplatform)
                implementation(shared.compose.material3.multiplatform)
                implementation(shared.compose.ui.tooling.preview.multiplatform)
                implementation(shared.compose.components.resources.multiplatform)

                implementation(shared.navigation3.ui.multiplatform)
                implementation(shared.compose.material.icons.core)

                implementation(shared.ionspin.kotlin.bignum)
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
