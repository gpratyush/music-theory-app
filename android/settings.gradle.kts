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
    // Versions live here so each module loads its plugins together (the Kotlin Android
    // plugin has to see the Android plugin's classes).
    plugins {
        id("com.android.application") version "8.10.1"
        kotlin("jvm") version "2.1.21"
        kotlin("android") version "2.1.21"
        kotlin("plugin.compose") version "2.1.21"
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
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

rootProject.name = "MusicTheoryAndroid"

include(":theory")
// The theory engine is plain Kotlin and builds anywhere. Set THEORY_ONLY=1 to skip the
// Android app (for machines without the Android SDK).
if (System.getenv("THEORY_ONLY") == null) {
    include(":app")
}
