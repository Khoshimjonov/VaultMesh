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

/**
 * The fs root the storage *explorer* and quota check operate on. For a cloud remote this is the
 * whole backend ("gdrive:"), so the user sees their entire Drive and its real free space; for a
 * local mirror it's the mirror folder itself. (Sync still uses the narrower [StorageTarget.fsRoot].)
 */
fun StorageTarget.rootFs(): String = when (kind) {
    TargetKind.RCLONE_REMOTE -> fsRoot.substringBefore(':') + ":"
    TargetKind.LOCAL_FOLDER -> fsRoot
}

@Serializable
data class RemoteEntry(val path: String, val name: String, val size: Long, val isDir: Boolean)

/**
 * Quota/usage for a backend, as reported by `rclone about`. Every field is optional — most cloud
 * providers report total/used/free, some only a subset, and a few (e.g. plain S3) report nothing.
 */
data class StorageUsage(
    val total: Long? = null,
    val used: Long? = null,
    val free: Long? = null,
    val trashed: Long? = null,
    val other: Long? = null,
    val objects: Long? = null,
) {
    val hasAny: Boolean get() = listOf(total, used, free, trashed, other, objects).any { it != null }
}

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

    /** Capacity/usage for [fs] (a root fs like "gdrive:" or a local path), or null if unreported. */
    suspend fun usage(fs: String): StorageUsage?

    /** Lists the entries directly under [subPath] within [fs] (for the storage explorer). */
    suspend fun list(fs: String, subPath: String): List<RemoteEntry>

    /**
     * Creates/authorizes a provider remote. For OAuth backends this opens the user's browser to
     * complete sign-in (the call blocks until they do); for credential backends [parameters] carry
     * the keys/secrets. Throws on failure (e.g. the user cancels authorization).
     */
    suspend fun createRemote(name: String, type: String, parameters: Map<String, String>)
}
