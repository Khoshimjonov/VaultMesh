package dev.vaultmesh.sync

import dev.vaultmesh.storage.StorageTarget
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Persists the list of replication targets as plain JSON (per-device config — folder paths and
 * remote names, no secrets). Lives outside the vault so it isn't itself replicated.
 */
class TargetStore(private val file: Path) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val serializer = ListSerializer(StorageTarget.serializer())

    fun load(): List<StorageTarget> =
        if (Files.isRegularFile(file)) {
            runCatching { json.decodeFromString(serializer, file.readText()) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }

    fun save(targets: List<StorageTarget>) {
        file.parent?.let { Files.createDirectories(it) }
        file.writeText(json.encodeToString(serializer, targets))
    }
}
