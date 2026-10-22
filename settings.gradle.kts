pluginManagement {
    repositories {
        google()
        // Google's mirror of Maven Central: same artifacts, avoids repo.maven.apache.org's
        // HTTP 429 rate limiting on bursty Gradle downloads. Maven Central stays as fallback.
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        // Google's mirror of Maven Central: same artifacts, avoids repo.maven.apache.org's
        // HTTP 429 rate limiting on bursty Gradle downloads. Maven Central stays as fallback.
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
    }
}

rootProject.name = "HomeLauncher"
include(":app")
