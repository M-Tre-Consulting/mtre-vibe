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

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Vibe"

// App Module
include(":app")

// Core Modules
include(":core:core-model")
include(":core:core-common")
include(":core:core-network")
include(":core:core-playback")
include(":core:core-connect")
include(":core:core-database")
include(":core:core-ui")

// Feature Modules
include(":feature:feature-home")
include(":feature:feature-search")
include(":feature:feature-library")
include(":feature:feature-playlist")
include(":feature:feature-artist")
include(":feature:feature-album")
include(":feature:feature-player")
include(":feature:feature-queue")
include(":feature:feature-lyrics")
include(":feature:feature-devices")
