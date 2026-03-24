pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

// Kover Aggregated Plugin: coverage for all modules (app + shared KMM host tests). Do not apply org.jetbrains.kotlinx.kover in build files.
// Run with -Pkover and test tasks, then koverHtmlReport/koverXmlReport. See https://kotlin.github.io/kotlinx-kover/gradle-plugin/aggregated.html
plugins {
    id("org.jetbrains.kotlinx.kover.aggregation") version "0.9.7" // https://github.com/Kotlin/kotlinx-kover/releases
}

kover {
    enableCoverage()
    reports {
        excludedProjects.addAll(":baselineprofile", ":benchmarks", ":benchmarks:databases")
    }
}
@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("org\\.chromium\\.net.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
        // TODO remove this once it's available in mavencentral
        maven { url = uri("https://maven.pkg.jetbrains.space/public/p/compose/dev/") }
    }
    versionCatalogs {
        create("shared") {
            from(files("gradle/shared.versions.toml"))
        }
        create("buildSrc") {
            from(files("gradle/buildSrc.versions.toml"))
        }
    }
}

rootProject.name = "Expenses"

include(":app")

include(":baselineprofile")
include(":benchmarks")
include(":benchmarks:databases")

include(":shared")
include(":shared:core")
include(":shared:core:analytics")
include(":shared:core:ktor-client-cronet")
include(":shared:core:locator")
include(":shared:core:navigation")
include(":shared:core:network")
include(":shared:core:storage-utils")
include(":shared:core:ui-design")
include(":shared:core:ui-utils")
include(":shared:core:utils")

include(":shared:feature")
include(":shared:feature:settings")
include(":shared:feature:events")
include(":shared:feature:expenses")
include(":shared:feature:menu")
include(":shared:feature:sync")
include(":shared:feature:share")

include(":shared:integration")
include(":shared:integration:databases")
include(":shared:integration:base")
