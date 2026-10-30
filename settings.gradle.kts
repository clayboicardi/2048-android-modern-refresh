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
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google()
        mavenCentral()
    }
}

check(JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_21)) {
    "JDK 21 is required (Robolectric SDK 36/37)"
}

rootProject.name = "game2048"

include(":app", ":core:engine")
