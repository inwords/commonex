import com.inwords.expenses.plugins.SharedKmmLibraryPlugin.Companion.applyKmmDefaults

plugins {
    id("shared-kmm-library-plugin")
}

kotlin {
    android {
        namespace = "com.inwords.expenses.feature.sync"

        @Suppress("UnstableApiUsage")
        optimization {
            consumerKeepRules.files.add(file("consumer-rules.pro"))
        }

        withHostTest {}
    }

    applyKmmDefaults("sharedSync")

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":shared:core:utils"))
                implementation(project(":shared:core:observability"))
                implementation(project(":shared:core:locator"))
                implementation(project(":shared:feature:events"))
                implementation(project(":shared:feature:expenses"))

                implementation(shared.coroutines.core)
            }
        }
        androidMain {
            dependencies {
                implementation(shared.work.runtime.ktx)
                implementation(shared.concurrent.futures.ktx)
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
    add("androidHostTestImplementation", shared.junit.jupiter.api)
    add("androidHostTestImplementation", shared.mockk)
    add("androidHostTestImplementation", shared.turbine)
    add("androidHostTestImplementation", shared.ionspin.kotlin.bignum)
}
