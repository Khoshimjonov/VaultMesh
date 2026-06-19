rootProject.name = "VaultMesh"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
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
