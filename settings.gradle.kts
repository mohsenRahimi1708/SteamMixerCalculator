pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal()
        mavenCentral()
        google()
    }
}

rootProject.name = "SteamMixerCalculator"
include(":app")
