package dev.vaultmesh.vault

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class MergeConflict(val originalPath: String, val conflictCopyPath: String)

data class MergeResult(val manifest: Manifest, val conflicts: List<MergeConflict>)

/**
 * Merges a remote manifest into the local one using per-file version vectors. Pure (no I/O), so
 * it's fully unit-testable.
 *
 * Per path:
 *  - present on one side only        -> kept as-is (remote-only entries are adopted)
 *  - identical content               -> kept, clocks merged
 *  - one vector dominates the other  -> fast-forward to the newer side
 *  - concurrent + different content  -> CONFLICT: keep local at its path, write the remote copy
 *    under "name (conflict <device> <date>).ext". Both kept entries get a vector that dominates
 *    the originals, so other devices fast-forward instead of re-conflicting. Never loses data.
 *
 * Note: deletions are not yet tracked (no tombstones) — a delete-aware merge is a later step.
 */
object ManifestMerger {

    fun merge(
        local: Manifest,
        remote: Manifest,
        thisDeviceId: String,
        timestampMs: Long = System.currentTimeMillis(),
    ): MergeResult {
        val localByPath = local.entries.associateBy { it.path }
        val remoteByPath = remote.entries.associateBy { it.path }

        val merged = ArrayList<FileEntry>()
        val conflicts = ArrayList<MergeConflict>()

        for (path in (localByPath.keys + remoteByPath.keys)) {
            val l = localByPath[path]
            val r = remoteByPath[path]
            when {
                l != null && r == null -> merged.add(l)
                l == null && r != null -> merged.add(r)
                l != null && r != null -> {
                    val lv = l.versionVector
                    val rv = r.versionVector
                    when {
                        l.sameContentAs(r) -> merged.add(l.copy(versionVector = VV.merge(lv, rv)))
                        VV.dominates(lv, rv) -> merged.add(l)
                        VV.dominates(rv, lv) -> merged.add(r)
                        else -> {
                            // Concurrent + different content: keep both, with a vector dominating both.
                            val winningVector = VV.bump(VV.merge(lv, rv), thisDeviceId)
                            when {
                                // Delete vs edit: preserve data — keep the surviving file, drop the tombstone.
                                l.deleted && !r.deleted -> merged.add(r.copy(versionVector = winningVector))
                                r.deleted && !l.deleted -> merged.add(l.copy(versionVector = winningVector))
                                else -> {
                                    val copyPath = conflictCopyPath(path, lv, rv, timestampMs)
                                    merged.add(l.copy(versionVector = winningVector))
                                    merged.add(r.copy(path = copyPath, conflictOf = path, versionVector = winningVector))
                                    conflicts.add(MergeConflict(path, copyPath))
                                }
                            }
                        }
                    }
                }
            }
        }

        return MergeResult(
            manifest = Manifest(
                version = maxOf(local.version, remote.version),
                entries = merged.sortedBy { it.path },
            ),
            conflicts = conflicts,
        )
    }

    private val stampFormat: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault())

    private fun conflictCopyPath(
        path: String,
        localVV: Map<String, Long>,
        remoteVV: Map<String, Long>,
        timestampMs: Long,
    ): String {
        // Label with the device that advanced the remote vector furthest beyond what local knew.
        val device = remoteVV.entries
            .filter { it.value > (localVV[it.key] ?: 0L) }
            .maxByOrNull { it.value - (localVV[it.key] ?: 0L) }?.key
            ?: "remote"
        val stamp = stampFormat.format(Instant.ofEpochMilli(timestampMs))
        val suffix = " (conflict $device $stamp)"
        val slash = path.lastIndexOf('/')
        val dot = path.lastIndexOf('.')
        return if (dot > slash && dot > 0) {
            path.substring(0, dot) + suffix + path.substring(dot)
        } else {
            path + suffix
        }
    }
}
