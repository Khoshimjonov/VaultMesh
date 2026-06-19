package dev.vaultmesh.storage.rclone

import dev.vaultmesh.storage.StorageEngine
import dev.vaultmesh.storage.StorageTarget
import dev.vaultmesh.storage.TransferResult
import java.nio.file.Path

/**
 * [StorageEngine] backed by a running rclone daemon. Push/pull mirror the whole (already
 * encrypted) vault directory using rclone's `sync/copy`, which handles chunked & resumable
 * transfers, retries, and only re-uploading changed files.
 */
class RcloneStorageEngine(private val rc: RcloneRc) : StorageEngine {

    override suspend fun push(localDir: Path, target: StorageTarget): TransferResult =
        runCatching { rc.syncCopy(localDir.toAbsolutePath().toString(), target.fsRoot) }
            .fold(
                onSuccess = { TransferResult(target, ok = true) },
                onFailure = { TransferResult(target, ok = false, error = it.message) },
            )

    override suspend fun pull(target: StorageTarget, localDir: Path): TransferResult =
        runCatching { rc.syncCopy(target.fsRoot, localDir.toAbsolutePath().toString()) }
            .fold(
                onSuccess = { TransferResult(target, ok = true) },
                onFailure = { TransferResult(target, ok = false, error = it.message) },
            )

    override suspend fun reachable(target: StorageTarget): Boolean =
        runCatching { rc.list(target.fsRoot) }.isSuccess

    override suspend fun listConfiguredRemotes(): List<String> = rc.listConfiguredRemotes()

    override suspend fun createRemote(name: String, type: String, parameters: Map<String, String>) =
        rc.configCreate(name, type, parameters)
}
