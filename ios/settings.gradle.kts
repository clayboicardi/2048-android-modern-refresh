// Standalone iOS sub-project (D-272). This settings file is NOT included by the root
// settings.gradle.kts; the root Android build (:app, :core:engine) never sees it.
// Invoke with `./gradlew -p ios …` from the repo root using the root Gradle wrapper.

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "game2048-ios"

include(":composeApp")
