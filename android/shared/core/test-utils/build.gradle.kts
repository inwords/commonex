import com.inwords.expenses.plugins.SharedKmmLibraryPlugin.Companion.applyKmmDefaults

plugins {
    id("shared-kmm-library-plugin")
}

kotlin {
    android {
        namespace = "com.inwords.expenses.core.testutils"

    }

    applyKmmDefaults("sharedCoreTestUtils")

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":shared:core:utils"))
            }
        }
    }
}
