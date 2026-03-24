import com.inwords.expenses.plugins.SharedKmmLibraryPlugin.Companion.applyKmmDefaults

plugins {
    id("shared-kmm-library-plugin")
}

kotlin {
    android {
        namespace = "com.inwords.expenses.core.analytics"
    }

    applyKmmDefaults("sharedCoreAnalytics")

    sourceSets {
        androidMain {
            dependencies {
                implementation(shared.posthog.android)
            }
        }
    }
}
