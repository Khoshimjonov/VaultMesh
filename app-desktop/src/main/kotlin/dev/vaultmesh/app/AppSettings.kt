package dev.vaultmesh.app

import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import kotlin.io.path.inputStream
import kotlin.io.path.isRegularFile
import kotlin.io.path.outputStream

/** Per-device app preferences (not secret). */
data class AppSettings(
    val autoSyncEnabled: Boolean = false,
    val autoSyncIntervalMinutes: Int = 5,
    /** Opt-in: keep the vault key in the OS keychain so launches skip the master password. */
    val stayUnlocked: Boolean = false,
)

/** Tiny Properties-backed store so we don't pull serialization into the UI module. */
class AppSettingsStore(private val file: Path) {

    fun load(): AppSettings {
        if (!file.isRegularFile()) return AppSettings()
        val props = Properties().apply { file.inputStream().use { load(it) } }
        return AppSettings(
            autoSyncEnabled = props.getProperty("autoSyncEnabled", "false").toBoolean(),
            autoSyncIntervalMinutes = props.getProperty("autoSyncIntervalMinutes", "5").toIntOrNull() ?: 5,
            stayUnlocked = props.getProperty("stayUnlocked", "false").toBoolean(),
        )
    }

    fun save(settings: AppSettings) {
        file.parent?.let { Files.createDirectories(it) }
        val props = Properties().apply {
            setProperty("autoSyncEnabled", settings.autoSyncEnabled.toString())
            setProperty("autoSyncIntervalMinutes", settings.autoSyncIntervalMinutes.toString())
            setProperty("stayUnlocked", settings.stayUnlocked.toString())
        }
        file.outputStream().use { props.store(it, "VaultMesh settings") }
    }
}
