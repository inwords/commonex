import com.android.build.api.dsl.ManagedVirtualDevice

plugins {
    id("shared-library-plugin")
    alias(shared.plugins.androidx.benchmark)
}

android {
    namespace = "com.inwords.expenses.benchmarks.network"

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
    implementation(project(":shared:core:ktor-client-cronet"))
    implementation(shared.ktor.client.core)
    implementation(shared.coroutines.core)
    implementation(shared.cronet.bundled)

    androidTestImplementation(shared.androidx.test.runner)
    androidTestImplementation(shared.androidx.test.ext.junit)
    androidTestImplementation(shared.androidx.test.benchmark.junit4)
    androidTestUtil(shared.androidx.test.orchestrator)
}
