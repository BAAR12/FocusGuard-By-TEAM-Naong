pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // This 'google()' line is the one that fixes your error
        google()
        mavenCentral()
    }
}
rootProject.name = "focus"
include(":app")

