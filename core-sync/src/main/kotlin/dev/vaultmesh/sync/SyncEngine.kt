package dev.vaultmesh.sync

import dev.vaultmesh.storage.StorageEngine
import dev.vaultmesh.storage.StorageTarget
import dev.vaultmesh.vault.Manifest
import dev.vaultmesh.vault.ManifestMerger
import dev.vaultmesh.vault.MergeConflict
import dev.vaultmesh.vault.Vault
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

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
 * Flow: pull the peer's encrypted vault into a staging dir → import any objects we don't have →
 * decrypt the peer manifest (same master key) → merge with version vectors (producing conflict
 * copies, never losing data) → write the merged manifest locally → push the merged vault back.
 *
 * Because content is encrypted and content-addressed, the peer is just an opaque blob store and
 * object merges are trivially safe.
 */
class SyncEngine(private val engine: StorageEngine) {

    suspend fun sync(localVault: Vault, target: StorageTarget, stagingDir: Path): SyncOutcome {
        Files.createDirectories(stagingDir)

        // 1. Pull the peer state. A failure here usually means the remote is empty (first sync).
        val pull = engine.pull(target, stagingDir)

        // 2. Adopt peer objects (content-addressed → no conflicts at the object layer).
        val imported = localVault.importObjects(stagingDir.resolve("objects"))

        // 3. Decrypt the peer manifest with our shared key (empty if remote had none yet).
        val stagedManifest = stagingDir.resolve("manifest.enc")
        val remoteManifest = if (stagedManifest.exists()) {
            runCatching { localVault.decryptManifest(stagedManifest) }.getOrDefault(Manifest())
        } else {
            Manifest()
        }

        // 4. Merge and persist locally.
        val result = ManifestMerger.merge(localVault.manifest, remoteManifest, localVault.deviceId)
        localVault.applyMergedManifest(result.manifest)

        // 5. Push the merged vault back so peers converge.
        val push = engine.push(localVault.root, target)

        return SyncOutcome(
            pulledOk = pull.ok,
            pushedOk = push.ok,
            importedObjects = imported,
            conflicts = result.conflicts,
            error = push.error ?: pull.error.takeIf { !push.ok },
        )
    }
}
