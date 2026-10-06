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
include(":otp-utils")
include(":otp-astar")
include(":otp-domain-core")
include(":otp-street")
include(":otp-routing")
include(":otp-raptor")
include(":graph-builder")
