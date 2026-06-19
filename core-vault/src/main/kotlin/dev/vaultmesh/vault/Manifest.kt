package dev.vaultmesh.vault

import kotlinx.serialization.Serializable

/**
 * A single entry in the vault's logical file tree. The plaintext path and metadata live ONLY
 * inside the encrypted manifest — storage providers never see file names or structure, only
 * opaque content-addressed objects.
 */
@Serializable
data class FileEntry(
    /** Logical path within the vault, '/'-separated, e.g. "Photos/2024/img.jpg". */
    val path: String,
    val isDir: Boolean = false,
    val size: Long = 0,
    val mtimeEpochMs: Long = 0,
    /** Ordered content-addressed chunk ids that reconstitute the file. Empty for dirs/empty files. */
    val chunkIds: List<String> = emptyList(),
    /**
     * Per-device edit counters (deviceId -> count). Used by the sync layer to distinguish a
     * fast-forward from a true concurrent edit. Empty for vaults created before sync existed.
     */
    val versionVector: Map<String, Long> = emptyMap(),
    /** Tombstone: a deleted entry is retained (with a bumped vector) so the delete propagates. */
    val deleted: Boolean = false,
    /** For a conflict copy, the original path it diverged from (so it can be resolved). */
    val conflictOf: String? = null,
) {
    /** Same content iff same ordered chunks and the same deleted state. */
    fun sameContentAs(other: FileEntry): Boolean =
        chunkIds == other.chunkIds && deleted == other.deleted
}

/** Version-vector helpers (a vector clock keyed by device id). */
object VV {
    /** True if [a] is at least as new as [b] on every device (a dominates or equals b). */
    fun dominates(a: Map<String, Long>, b: Map<String, Long>): Boolean =
        (a.keys + b.keys).all { (a[it] ?: 0L) >= (b[it] ?: 0L) }

    /** True if neither dominates the other — i.e. concurrent edits happened. */
    fun concurrent(a: Map<String, Long>, b: Map<String, Long>): Boolean =
        !dominates(a, b) && !dominates(b, a)

    /** Componentwise maximum — the merged history of both vectors. */
    fun merge(a: Map<String, Long>, b: Map<String, Long>): Map<String, Long> =
        (a.keys + b.keys).associateWith { maxOf(a[it] ?: 0L, b[it] ?: 0L) }

    /** Increment this device's counter (records a new local edit). */
    fun bump(v: Map<String, Long>, deviceId: String): Map<String, Long> =
        v + (deviceId to (v[deviceId] ?: 0L) + 1L)
}

/** The decrypted index of everything in the vault. Persisted as the encrypted `manifest.enc`. */
@Serializable
data class Manifest(
    val version: Int = 1,
    val entries: List<FileEntry> = emptyList(),
) {
    fun upsert(entry: FileEntry): Manifest =
        copy(entries = entries.filterNot { it.path == entry.path } + entry)

    fun remove(path: String): Manifest =
        copy(entries = entries.filterNot { it.path == path || it.path.startsWith("$path/") })
}
