plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":storage-spi"))
    api(project(":core-vault"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(project(":storage-rclone"))
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    val devBinary = rootProject.layout.projectDirectory.dir("tools/rclone-bin").file("rclone").asFile
    if (devBinary.exists()) systemProperty("vaultmesh.rclone.path", devBinary.absolutePath)
}
