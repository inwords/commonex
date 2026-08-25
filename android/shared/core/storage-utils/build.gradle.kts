import com.inwords.expenses.plugins.SharedKmmLibraryPlugin.Companion.applyKmmDefaults

plugins {
    id("shared-kmm-library-plugin")
}

kotlin {
    android {
        namespace = "com.inwords.expenses.core.storage.utils"

    }

    applyKmmDefaults("sharedCoreStorageUtils")

    sourceSets {
        commonMain {
            dependencies {
                implementation(shared.kotlinx.datetime)

                implementation(shared.room.runtime)

                implementation(shared.datastore.core.okio)
                implementation(shared.kotlinx.atomicfu)

                implementation(shared.ionspin.kotlin.bignum)
            }
        }
    }
}
