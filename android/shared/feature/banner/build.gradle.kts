import com.inwords.expenses.plugins.SharedKmmLibraryPlugin.Companion.applyKmmDefaults

plugins {
    id("shared-kmm-library-plugin")
    alias(shared.plugins.kotlin.serialization)
    alias(shared.plugins.compose.compiler)
    alias(shared.plugins.compose.multiplatform.compiler)
}

kotlin {
    android {
        namespace = "com.inwords.expenses.feature.banner"

        experimentalProperties["android.experimental.kmp.enableAndroidResources"] = true

        withHostTest {}
    }

    applyKmmDefaults("sharedBanner")

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":shared:core:utils"))
                implementation(project(":shared:core:ui-utils"))
                implementation(project(":shared:core:ui-design"))
                implementation(project(":shared:core:network"))
                implementation(project(":shared:feature:settings"))

                implementation(shared.coroutines.core)

                implementation(shared.kotlinx.serialization.json)

                implementation(shared.ktor.client.core)

                implementation(shared.compose.ui.multiplatform)
                implementation(shared.compose.material3.multiplatform)
                implementation(shared.compose.ui.tooling.preview.multiplatform)
                implementation(shared.compose.components.resources.multiplatform)

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
}

compose.resources {
    publicResClass = true
}
