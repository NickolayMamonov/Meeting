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

rootProject.name = "Meet"
include(":app")
include(":uikit")
include(":features:meetings:presentation")
include(":core:data")
include(":core:domain")
include(":features:communities")
include(":features:profile")
include(":features:auth")
include(":core:common")
include(":core:network")
include(":core:auth")
include(":features:meetings:domain")
include(":features:meetings:data")
