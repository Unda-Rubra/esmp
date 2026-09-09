pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/") {
            content {
                includeGroupByRegex("io\\.papermc.*")
                includeGroup("com.mojang")
                includeGroup("net.md-5")
            }
        }
    }
}

rootProject.name = "esmp"
include("esmp-core", "esmp-civilization", "esmp-resource", "esmp-plugin")
