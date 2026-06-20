package dev.vaultmesh.sync

import dev.vaultmesh.storage.StorageEngine
import dev.vaultmesh.storage.StorageTarget
import dev.vaultmesh.vault.MergeConflict
import dev.vaultmesh.vault.Vault
import java.nio.file.Files
import java.nio.file.Path

data class SyncOutcome(
    val pulledOk: Boolean,
    val pushedOk: Boolean,
    val importedObjects: Int,
    val conflicts: List<MergeConflict>,
    val error: String? = null,
)

/**
 * Two-way sync of a vault against one shared target (a cloud remote or folder both devices reach).
 *
 * Flow: pull the peer's encrypted vault into a staging dir → [Vault.mergeFrom] (import objects,
 * decrypt the peer manifest with the shared key, merge with version vectors producing conflict
 * copies, persist) → push the merged vault back.
 *
 * The pull and push are network I/O and hold no lock; only the in-memory merge mutates local state.
 * The app layer runs many targets in parallel and serializes just the [Vault.mergeFrom] step.
 */
class SyncEngine(private val engine: StorageEngine) {

    suspend fun sync(localVault: Vault, target: StorageTarget, stagingDir: Path): SyncOutcome {
        Files.createDirectories(stagingDir)
        val pull = engine.pull(target, stagingDir)
        val merge = localVault.mergeFrom(stagingDir)
        val push = engine.push(localVault.root, target)
        return SyncOutcome(
            pulledOk = pull.ok,
            pushedOk = push.ok,
            importedObjects = merge.imported,
            conflicts = merge.conflicts,
            error = push.error ?: pull.error.takeIf { !push.ok },
        )
    }
}
