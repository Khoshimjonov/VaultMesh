import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.io.File
import java.net.URI

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core-vault")) // transitively brings core-crypto
    implementation(project(":core-sync")) // replication + storage SPI
    implementation(project(":storage-rclone")) // rclone engine

    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(libs.kotlinx.coroutines.swing)
}

// Host platform → (Compose appResources subdir, rclone download arch, executable name).
val hostOs = System.getProperty("os.name").lowercase()
val hostArm = System.getProperty("os.arch").lowercase().let { it.contains("aarch64") || it.contains("arm") }
val rcloneExe = if (hostOs.contains("win")) "rclone.exe" else "rclone"
val composeResDir = when {
    hostOs.contains("mac") || hostOs.contains("darwin") -> if (hostArm) "macos-arm64" else "macos-x64"
    hostOs.contains("win") -> "windows-x64"
    else -> if (hostArm) "linux-arm64" else "linux-x64"
}
val rcloneArch = when {
    hostOs.contains("mac") || hostOs.contains("darwin") -> if (hostArm) "osx-arm64" else "osx-amd64"
    hostOs.contains("win") -> "windows-amd64"
    else -> if (hostArm) "linux-arm64" else "linux-amd64"
}

// Ensures the rclone binary for this host is present under resources/<os-arch>/ so it gets bundled
// into the installer. Copies the dev binary (tools/rclone-bin) if present, otherwise downloads it.
val downloadRclone by tasks.registering {
    val outBin = layout.projectDirectory.dir("resources").dir(composeResDir).file(rcloneExe).asFile
    val devBin = rootProject.layout.projectDirectory.dir("tools/rclone-bin").file(rcloneExe).asFile
    outputs.file(outBin)
    doLast {
        if (outBin.exists() && outBin.length() > 0L) return@doLast
        outBin.parentFile.mkdirs()
        if (devBin.exists() && devBin.length() > 0L) {
            devBin.copyTo(outBin, overwrite = true)
        } else {
            val zip = File(temporaryDir, "rclone.zip")
            URI("https://downloads.rclone.org/rclone-current-$rcloneArch.zip").toURL().openStream()
                .use { input -> zip.outputStream().use { input.copyTo(it) } }
            copy { from(zipTree(zip)); into(temporaryDir) }
            val extracted = temporaryDir.walkTopDown().first { it.isFile && it.name == rcloneExe }
            extracted.copyTo(outBin, overwrite = true)
        }
        outBin.setExecutable(true)
    }
}

// Make sure the binary is staged before the app resources are assembled (covers run + packaging).
tasks.matching { it.name == "prepareAppResources" }.configureEach { dependsOn(downloadRclone) }

compose.desktop {
    application {
        mainClass = "dev.vaultmesh.app.MainKt"

        // In a dev `./gradlew run`, point at the dev binary directly (the bundled resource also works).
        rootProject.layout.projectDirectory.dir("tools/rclone-bin").file("rclone").asFile
            .takeIf { it.exists() }
            ?.let { jvmArgs("-Dvaultmesh.rclone.path=${it.absolutePath}") }

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "VaultMesh"
            packageVersion = "1.0.0"
            description = "Encrypted multi-storage personal vault"
            vendor = "VaultMesh"

            // Bundle a full runtime so the installed app needs nothing preinstalled.
            includeAllModules = true

            // rclone (and any future assets) live here; the matching os-arch subdir is bundled.
            appResourcesRootDir.set(project.layout.projectDirectory.dir("resources"))

            macOS {
                bundleID = "dev.vaultmesh.desktop"
                dockName = "VaultMesh"
            }
            windows {
                menuGroup = "VaultMesh"
                // Stable UUID so upgrades replace rather than duplicate the install.
                upgradeUuid = "7E6F6A2C-2D2E-4C2A-9B1E-2C9C4B4E1A11"
            }
            linux {
                packageName = "vaultmesh"
            }
        }
    }
}
