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
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        ivy {
            name = "OpenRQGitHubRelease"
            url = uri("https://github.com/openrq-team/OpenRQ/releases/download/3.3.2")
            patternLayout {
                artifact("[artifact]-[revision].[ext]")
            }
            metadataSources {
                artifact()
            }
            content {
                includeModule("openrq", "openrq")
            }
        }
    }
}
rootProject.name = "OptiFrame"
include(":app")
include(":protocol-core")
include(":optional-openrq")
