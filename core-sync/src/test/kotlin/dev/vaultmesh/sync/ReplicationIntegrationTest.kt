package dev.vaultmesh.sync

import dev.vaultmesh.crypto.KdfParams
import dev.vaultmesh.storage.StorageTarget
import dev.vaultmesh.storage.TargetKind
import dev.vaultmesh.storage.rclone.RcloneBinary
import dev.vaultmesh.storage.rclone.RcloneDaemon
import dev.vaultmesh.storage.rclone.RcloneStorageEngine
import dev.vaultmesh.vault.VaultFactory
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes
import kotlin.random.Random
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

/**
 * Exercises the full Phase 2 transport with the real rclone binary, using local folders as stand-in
 * "remotes" so no cloud credentials are needed. Proves: encrypt -> replicate to N targets -> restore
 * from one -> decrypt matches the original.
 */
class ReplicationIntegrationTest {

    private val password = "replication-test-passphrase".toCharArray()
    private val testKdf: (ByteArray) -> KdfParams = { KdfParams.forTesting(it) }

    @Test
    fun `replicate to two local remotes and restore`(@TempDir tmp: Path) = runBlocking {
        assumeTrue(RcloneBinary.isAvailable(), "rclone binary not available; skipping")

        val vaultDir = tmp.resolve("vault")
        val remoteA = tmp.resolve("remoteA")
        val remoteB = tmp.resolve("remoteB")
        val restored = tmp.resolve("restored")

        // Source data.
        val src = tmp.resolve("src").also { it.createDirectories() }
        val payload = Random(99).nextBytes(2 * 1024 * 1024 + 17) // multi-chunk
        src.resolve("secret.bin").writeBytes(payload)
        src.resolve("note.txt").writeBytes("top secret note".toByteArray())

        // Build an encrypted vault.
        VaultFactory.create(vaultDir, password, testKdf).vault.use { it.addFolder(src, "docs") }

        RcloneDaemon.start(configPath = tmp.resolve("rclone.conf")).use { daemon ->
            val replicator = VaultReplicator(RcloneStorageEngine(daemon.rc))
            val targets = listOf(
                StorageTarget(UUID.randomUUID().toString(), "Remote A", TargetKind.LOCAL_FOLDER, remoteA.toString()),
                StorageTarget(UUID.randomUUID().toString(), "Remote B", TargetKind.LOCAL_FOLDER, remoteB.toString()),
            )

            val results = replicator.replicate(vaultDir, targets)
            assertTrue(results.all { it.ok }, "all pushes should succeed: $results")

            // Both remotes hold the encrypted vault structure.
            for (remote in listOf(remoteA, remoteB)) {
                assertTrue(remote.resolve("vault.json").exists(), "vault.json missing in $remote")
                assertTrue(remote.resolve("manifest.enc").exists(), "manifest.enc missing in $remote")
                val objectCount = Files.list(remote.resolve("objects")).use { it.count() }
                assertTrue(objectCount > 0, "no encrypted objects in $remote")
            }

            // Restore from Remote A into a fresh location and decrypt.
            val restoreResult = replicator.restore(targets[0], restored)
            assertTrue(restoreResult.ok, "restore failed: $restoreResult")

            VaultFactory.unlockWithPassword(restored, password).use { vault ->
                val out = tmp.resolve("out.bin")
                vault.exportFile("docs/secret.bin", out)
                assertContentEquals(payload, out.readBytes())
            }
        }
    }
}
