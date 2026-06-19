// Root build script. Declares plugins (applied in subprojects) without applying them here.
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.compose) apply false
}

group = "dev.vaultmesh"
version = "0.1.0-SNAPSHOT"
