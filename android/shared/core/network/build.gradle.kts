import com.inwords.expenses.plugins.SharedKmmLibraryPlugin.Companion.applyKmmDefaults

plugins {
    id("shared-kmm-library-plugin")
    alias(shared.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.inwords.expenses.core.network"

        @Suppress("UnstableApiUsage")
        optimization {
            consumerKeepRules.files.add(file("consumer-rules.pro"))
        }

        withHostTest {}
    }

    applyKmmDefaults("sharedCoreNetwork")

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":shared:core:utils"))

                implementation(shared.ktor.client.content.negotiation)
                implementation(shared.ktor.client.logging)
                implementation(shared.ktor.serialization.kotlinx.json)
            }
        }
        androidMain {
            dependencies {
                implementation(project(":shared:core:ktor-client-cronet"))

                implementation(shared.ktor.client.logging.jvm)
                implementation(shared.cronet.api)
            }
        }
        iosMain.dependencies {
            implementation(shared.ktor.client.darwin)
        }
        commonTest {
            dependencies {
                implementation(shared.kotlin.test)
                implementation(shared.coroutines.test)
                implementation(shared.ktor.client.mock)
                implementation(shared.kotlinx.serialization.json)
            }
        }
    }

    compilerOptions {
        // Common compiler options applied to all Kotlin source sets
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}

dependencies {
    add("androidHostTestImplementation", shared.kotlin.test)
    add("androidHostTestImplementation", shared.coroutines.test)
    add("androidHostTestImplementation", shared.ktor.client.mock)
}
