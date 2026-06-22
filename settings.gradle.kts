rootProject.name = "VaultMesh"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

// Auto-provision the JDK 17 toolchain when it isn't installed locally, so the build keeps working
// regardless of which JDK happens to be the machine default (Gradle itself can run on a newer JDK).
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

include(":core-crypto")
include(":core-vault")
include(":storage-spi")
include(":storage-rclone")
include(":core-sync")
include(":app-desktop")
