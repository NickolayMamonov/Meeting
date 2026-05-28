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
include(":core:common")
include(":core:network")
include(":core:auth")
include(":features:auth")
include(":features:meetings:data")
include(":features:meetings:domain")
include(":features:meetings:presentation")
include(":features:profile:presentation")
include(":features:communities:domain")
include(":features:communities:presentation")
include(":features:communities:data")
include(":features:profile:domain")
include(":features:profile:data")
include(":core:domain")
include(":core:data")
include(":core:testing")
