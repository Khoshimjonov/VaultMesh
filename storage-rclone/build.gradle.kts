plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":storage-spi"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(project(":core-vault"))
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    // Let the integration test find the dev rclone binary if not on PATH.
    val devBinary = rootProject.layout.projectDirectory.dir("tools/rclone-bin").file("rclone").asFile
    if (devBinary.exists()) systemProperty("vaultmesh.rclone.path", devBinary.absolutePath)
}
