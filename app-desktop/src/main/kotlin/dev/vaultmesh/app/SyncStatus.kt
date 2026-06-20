package dev.vaultmesh.app

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.readLines
import kotlin.io.path.writeText

/** The sync state of a single file or folder, used to render a badge in the file list. */
enum class FileSyncState {
    /** No storage targets are enabled — the file lives only on this device. */
    LocalOnly,

    /** Changed since the last successful sync and waiting to be pushed. */
    Pending,

    /** A sync is in progress and this item is part of it. */
    Syncing,

    /** Present on every enabled target as of the last successful sync. */
    Synced,

    /** The last sync attempt failed for this item's targets. */
    Error,
}

/**
 * Per-device bookkeeping of which vault paths still need to reach the storage targets. The vault
 * itself is synced whole-directory by rclone, so this is an approximation layered on top: a path is
 * "pending" from the moment it changes locally until a sync to *every* enabled target succeeds, at
 * which point the whole pending set is cleared.
 *
 * This is local-only state (never uploaded) — newline format, no serialization dependency.
 */
class SyncStatusStore(private val file: Path) {

    data class State(val everSynced: Boolean, val pending: Set<String>)

    fun load(): State {
        if (!file.isRegularFile()) return State(everSynced = false, pending = emptySet())
        val lines = file.readLines()
        val ever = lines.firstOrNull()?.removePrefix("everSynced=")?.toBoolean() ?: false
        val pending = lines.drop(1).filter { it.isNotBlank() }.toSet()
        return State(ever, pending)
    }

    fun save(state: State) {
        file.parent?.let { Files.createDirectories(it) }
        file.writeText("everSynced=${state.everSynced}\n" + state.pending.joinToString("\n"))
    }
}
