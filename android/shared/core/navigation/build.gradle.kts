import com.inwords.expenses.plugins.SharedKmmLibraryPlugin.Companion.applyKmmDefaults

plugins {
    id("shared-kmm-library-plugin")
    alias(shared.plugins.kotlin.serialization)
    alias(shared.plugins.compose.compiler)
    alias(shared.plugins.compose.multiplatform.compiler)
}

kotlin {
    android {
        namespace = "com.inwords.expenses.core.navigation"

        @Suppress("UnstableApiUsage")
        optimization {
            consumerKeepRules.files.add(file("consumer-rules.pro"))
        }
    }

    applyKmmDefaults("sharedCoreNavigation")

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":shared:core:utils"))
                implementation(project(":shared:core:observability"))

                // TODO the whole ktor for URL parsing
                implementation(shared.ktor.client.core)

                implementation(shared.kotlinx.serialization.json)

                // for BottomSheetScene
                implementation(shared.compose.material3.multiplatform)

                implementation(shared.lifecycle.viewmodel.compose.multiplatform)
                implementation(shared.navigation3.ui.multiplatform)
            }
        }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}
