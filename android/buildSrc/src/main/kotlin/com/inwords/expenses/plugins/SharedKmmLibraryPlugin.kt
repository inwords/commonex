package com.inwords.expenses.plugins

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class SharedKmmLibraryPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        project.pluginManager.apply("com.android.kotlin.multiplatform.library")
        project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")

        val kotlin = project.extensions.getByType<KotlinMultiplatformExtension>()
        val kotlinAndroid = kotlin.extensions.getByType<KotlinMultiplatformAndroidLibraryTarget>()

        kotlin.apply {
            compilerOptions {
                extraWarnings.set(true)
                freeCompilerArgs.addAll(
                    "-Xreturn-value-checker=check",
                    "-Xexplicit-backing-fields",
                )
            }
        }

        kotlinAndroid.apply {
            compileSdk = 36
            minSdk = 26

            compilations.configureEach {
                compileTaskProvider.configure {
                    compilerOptions {
                        jvmTarget.set(JvmTarget.JVM_17)
                    }
                }
            }

            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_17)
            }
        }
    }

    companion object {

        fun KotlinMultiplatformExtension.applyKmmDefaults(iosBaseName: String) {
            listOf(
                iosArm64(),
                iosSimulatorArm64()
            ).forEach {
                it.binaries.framework {
                    baseName = iosBaseName
                    isStatic = true
                }
            }

            sourceSets.apply {
                iosArm64()
                iosSimulatorArm64()
            }
        }
    }
}
