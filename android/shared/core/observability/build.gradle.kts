import com.inwords.expenses.plugins.SharedKmmLibraryPlugin.Companion.applyKmmDefaults
import io.sentry.kotlin.multiplatform.gradle.AppleDependencyProvider

plugins {
    id("shared-kmm-library-plugin")
    alias(shared.plugins.sentry.kotlin.multiplatform)
}

sentryKmp {
    autoInstall.apple.provider.set(AppleDependencyProvider.NONE)
}

kotlin {
    android {
        namespace = "com.inwords.expenses.core.observability"
        withHostTest {}
    }

    applyKmmDefaults("sharedCoreObservability")

    sourceSets {
        commonTest {
            dependencies {
                implementation(shared.kotlin.test)
            }
        }
    }
}
