package dev.vaultmesh.storage

import kotlinx.serialization.Serializable
import java.nio.file.Path

/**
 * Where a vault is replicated. A target is fully described by its rclone `fs` root string, which
 * may be a local path ("/Volumes/Backup/VaultMesh") or a configured remote ("gdrive:VaultMesh").
 * This keeps every backend — local disk or any of rclone's 70+ providers — behind one model.
 */
@Serializable
data class StorageTarget(
    val id: String,
    val displayName: String,
    val kind: TargetKind,
    /** rclone fs root: an absolute local path, or "<remote>:<path>". */
    val fsRoot: String,
    val enabled: Boolean = true,
)

enum class TargetKind { LOCAL_FOLDER, RCLONE_REMOTE }

@Serializable
data class RemoteEntry(val path: String, val name: String, val size: Long, val isDir: Boolean)

data class TransferResult(
    val target: StorageTarget,
    val ok: Boolean,
    val error: String? = null,
)

/**
 * Transport abstraction. The rclone-backed implementation lives in `storage-rclone`; a native
 * per-provider implementation could be dropped in later without touching the sync layer.
 */
interface StorageEngine {
    /** Mirror [localDir] up to the target (additive copy — does not delete remote-only files). */
    suspend fun push(localDir: Path, target: StorageTarget): TransferResult

    /** Mirror the target down into [localDir]. */
    suspend fun pull(target: StorageTarget, localDir: Path): TransferResult

    /** True if the target is reachable (path exists / remote responds). */
    suspend fun reachable(target: StorageTarget): Boolean

    /** Names of rclone remotes already configured on this machine (for the "add cloud" picker). */
    suspend fun listConfiguredRemotes(): List<String>

    /**
     * Creates/authorizes a provider remote. For OAuth backends this opens the user's browser to
     * complete sign-in (the call blocks until they do); for credential backends [parameters] carry
     * the keys/secrets. Throws on failure (e.g. the user cancels authorization).
     */
    suspend fun createRemote(name: String, type: String, parameters: Map<String, String>)
}
