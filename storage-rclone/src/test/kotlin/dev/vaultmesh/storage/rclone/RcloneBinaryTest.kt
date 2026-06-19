package dev.vaultmesh.storage.rclone

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RcloneBinaryTest {

    /** The installed app finds rclone bundled in its resources dir and copies it somewhere writable+executable. */
    @Test
    @DisabledOnOs(OS.WINDOWS) // executable-bit semantics differ on Windows
    fun `locate materializes a bundled binary into a writable home`(@TempDir tmp: Path) {
        val savedOverride = System.getProperty("vaultmesh.rclone.path")
        try {
            System.clearProperty("vaultmesh.rclone.path") // the build sets this for other tests

            val resources = tmp.resolve("resources").also { Files.createDirectories(it) }
            Files.writeString(resources.resolve("rclone"), "#!/bin/sh\necho fake\n")
            val home = tmp.resolve("home")

            System.setProperty("compose.application.resources.dir", resources.toString())
            System.setProperty("vaultmesh.home", home.toString())

            val located = RcloneBinary.locate()

            assertEquals(home.resolve("bin").resolve("rclone"), located)
            assertTrue(Files.isRegularFile(located), "binary should be materialized")
            assertTrue(located.toFile().canExecute(), "materialized binary should be executable")
        } finally {
            System.clearProperty("compose.application.resources.dir")
            System.clearProperty("vaultmesh.home")
            savedOverride?.let { System.setProperty("vaultmesh.rclone.path", it) }
        }
    }
}
