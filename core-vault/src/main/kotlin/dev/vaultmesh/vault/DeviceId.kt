package dev.vaultmesh.vault

import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * A stable identifier for this installation, used as the key in per-file version vectors so the
 * sync layer can tell this device's edits apart from others'. Persisted once, then reused.
 */
object DeviceId {
    fun loadOrCreate(file: Path): String {
        if (Files.isRegularFile(file)) {
            val existing = file.readText().trim()
            if (existing.isNotEmpty()) return existing
        }
        val id = UUID.randomUUID().toString()
        file.parent?.let { Files.createDirectories(it) }
        file.writeText(id)
        return id
    }

    /** Default location for the desktop app. */
    fun defaultFile(): Path =
        Path.of(System.getProperty("user.home"), ".vaultmesh", "config", "device-id")
}
