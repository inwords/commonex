import com.android.build.api.dsl.ManagedVirtualDevice
import com.inwords.expenses.plugins.SharedKmmLibraryPlugin.Companion.applyKmmDefaults

plugins {
    id("shared-kmm-library-plugin")
    alias(shared.plugins.ksp)
    alias(shared.plugins.room)
}

kotlin {
    android {
        namespace = "com.inwords.expenses.integration.databases"

        @Suppress("UnstableApiUsage")
        optimization {
            consumerKeepRules.files.add(file("consumer-rules.pro"))
        }

        androidResources {
            enable = true
        }

        withDeviceTest {
            animationsDisabled = true
            execution = "ANDROIDX_TEST_ORCHESTRATOR"

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

    applyKmmDefaults("sharedIntegrationDatabases")

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":shared:core:utils"))
                implementation(project(":shared:core:storage-utils"))
                implementation(project(":shared:feature:expenses"))
                implementation(project(":shared:feature:events"))

                // db
                implementation(shared.room.runtime)
                implementation(shared.sqlite.bundled)

                implementation(shared.annotation)

                implementation(shared.coroutines.core)

                implementation(shared.kotlinx.datetime)

                implementation(shared.ionspin.kotlin.bignum)
            }
        }
        androidMain {
            dependencies {
                // FIXME: remove https://github.com/google/ksp/issues/1896
                val composeBom = project.dependencies.platform(shared.compose.bom)
                implementation(composeBom)
                implementation(shared.compose.runtime)
            }
        }
        @Suppress("unused")
        val androidDeviceTest by getting {
            dependencies {
                implementation(shared.room.testing)
                implementation(shared.androidx.test.runner)
                implementation(shared.androidx.test.ext.junit)
            }
        }
    }

    compilerOptions {
        // Common compiler options applied to all Kotlin source sets
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}

dependencies {
    // TODO is there a better way to do this?
    kspAndroid(shared.room.compiler)
    add("kspIosArm64", shared.room.compiler)
    add("kspIosSimulatorArm64", shared.room.compiler)

    androidTestUtil(shared.androidx.test.orchestrator)
}

room {
    schemaDirectory("$projectDir/schemas")
}
