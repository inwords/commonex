import com.inwords.expenses.plugins.SharedKmmLibraryPlugin.Companion.applyKmmDefaults

plugins {
    id("shared-kmm-library-plugin")
    alias(shared.plugins.sentry.kotlin.multiplatform)
}

kotlin {
    android {
        namespace = "com.inwords.expenses.core.observability"
    }

    applyKmmDefaults("sharedCoreObservability")
}
