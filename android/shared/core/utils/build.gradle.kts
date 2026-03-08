import com.inwords.expenses.plugins.SharedKmmLibraryPlugin.Companion.applyKmmDefaults

plugins {
    id("shared-kmm-library-plugin")
}

kotlin {
    android {
        namespace = "com.inwords.expenses.core.utils"

        @Suppress("UnstableApiUsage")
        optimization {
            consumerKeepRules.files.add(file("consumer-rules.pro"))
        }

        withHostTest {}
    }

    applyKmmDefaults("sharedCoreUtils")

    sourceSets {
        commonMain {
            dependencies {
                implementation(shared.coroutines.core)

                implementation(shared.kotlinx.collections.immutable)

                implementation(shared.ionspin.kotlin.bignum)
            }
        }
        commonTest {
            dependencies {
                implementation(shared.kotlin.test)
                implementation(shared.ionspin.kotlin.bignum)
            }
        }
    }
}
