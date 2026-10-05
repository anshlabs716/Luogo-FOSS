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

rootProject.name = "Luogo-FOSS"

// Relay logic shared by the Android client and the standalone server, so both enforce the
// same authorisation rules instead of drifting apart.
include(":shared")
include(":relay")

// Configuring :app requires the Android Gradle plugin and an installed SDK, neither of which
// the relay container image has. Skipping the module keeps the image free of the SDK and keeps
// the server build honest about only depending on :shared.
if (providers.gradleProperty("relayOnly").orNull != "true") {
    include(":app")
}
