import com.android.build.api.dsl.ManagedVirtualDevice

plugins {
    id("shared-library-plugin")
    alias(shared.plugins.ksp)
    alias(shared.plugins.room)
    alias(shared.plugins.androidx.benchmark)
}

android {
    namespace = "com.inwords.expenses.benchmarks.databases"

    testBuildType = "release"

    testOptions {
        animationsDisabled = true

        @Suppress("UnstableApiUsage")
        managedDevices {
            allDevices {
                create<ManagedVirtualDevice>("pixel6Api35Atd") {
                    device = "Pixel 6"
                    apiLevel = 35
                    systemImageSource = "aosp-atd"
                    testedAbi = "x86_64"
                }
            }
        }
    }
}

dependencies {
    implementation(shared.room.runtime)
    implementation(shared.sqlite.bundled)
    implementation(shared.coroutines.core)
    implementation(shared.annotation)

    ksp(shared.room.compiler)

    androidTestImplementation(shared.androidx.test.runner)
    androidTestImplementation(shared.androidx.test.ext.junit)
    androidTestImplementation(shared.androidx.test.benchmark.junit4)
    androidTestUtil(shared.androidx.test.orchestrator)
}

room {
    schemaDirectory("$projectDir/schemas")
}
