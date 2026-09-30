pluginManagement {
    repositories {
        maven("https://cache-redirector.jetbrains.com/plugins.gradle.org")
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        maven("https://cache-redirector.jetbrains.com/maven-central")
        mavenCentral()
    }
}

rootProject.name = "otp-server-ui"
include(":backend")
