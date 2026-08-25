import com.inwords.expenses.plugins.SharedKmmLibraryPlugin.Companion.applyKmmDefaults

plugins {
    id("shared-kmm-library-plugin")
    alias(shared.plugins.wire)
}

kotlin {
    android {
        namespace = "com.inwords.expenses.feature.settings"

    }

    applyKmmDefaults("sharedSettings")

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":shared:core:utils"))
                implementation(project(":shared:core:storage-utils"))

                implementation(shared.datastore.core.okio)
            }
        }
        androidMain {
            dependencies {
                implementation(shared.datastore.android)
            }
        }
    }

    compilerOptions {
        // Common compiler options applied to all Kotlin source sets
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}

wire {
    kotlin {}
    sourcePath {
        srcDir(listOf("src/commonMain/proto"))
    }
}
