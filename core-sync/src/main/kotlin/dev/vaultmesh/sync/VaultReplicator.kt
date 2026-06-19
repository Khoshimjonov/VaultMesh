package dev.vaultmesh.sync

import dev.vaultmesh.storage.StorageEngine
import dev.vaultmesh.storage.StorageTarget
import dev.vaultmesh.storage.TransferResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.nio.file.Path

/**
 * Phase 2 replication: mirror the encrypted vault directory out to every enabled target in
 * parallel (redundancy — any single target can restore the vault), and pull a vault back down.
 *
 * Because the vault directory is fully encrypted before it ever reaches here, each target is just
 * an opaque blob mirror. Phase 3 layers two-way sync (commit DAG + conflict resolution) on top of
 * this same transport.
 */
class VaultReplicator(private val engine: StorageEngine) {

    /** Push the vault to all enabled targets concurrently; one failure doesn't abort the others. */
    suspend fun replicate(vaultDir: Path, targets: List<StorageTarget>): List<TransferResult> =
        coroutineScope {
            targets.filter { it.enabled }
                .map { target -> async(Dispatchers.IO) { engine.push(vaultDir, target) } }
                .awaitAll()
        }

    /** Restore (pull) the vault from a single target into [vaultDir]. */
    suspend fun restore(target: StorageTarget, vaultDir: Path): TransferResult =
        engine.pull(target, vaultDir)
}
