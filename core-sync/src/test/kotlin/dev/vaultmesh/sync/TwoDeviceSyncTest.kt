package dev.vaultmesh.sync

import dev.vaultmesh.crypto.KdfParams
import dev.vaultmesh.storage.StorageTarget
import dev.vaultmesh.storage.TargetKind
import dev.vaultmesh.storage.rclone.RcloneBinary
import dev.vaultmesh.storage.rclone.RcloneDaemon
import dev.vaultmesh.storage.rclone.RcloneStorageEngine
import dev.vaultmesh.vault.Vault
import dev.vaultmesh.vault.VaultFactory
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.createDirectories
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Simulates two devices sharing one rclone "remote" (a local folder, no creds) to prove the
 * end-to-end two-way sync: a clean fast-forward, and a genuine concurrent edit that produces a
 * conflict copy with both versions preserved.
 */
class TwoDeviceSyncTest {

    private val password get() = "two-device-sync-pass".toCharArray()
    private val testKdf: (ByteArray) -> KdfParams = { KdfParams.forTesting(it) }

    private fun writeSource(dir: Path, content: ByteArray): Path {
        dir.createDirectories()
        val f = dir.resolve("source-${UUID.randomUUID()}.bin")
        f.writeBytes(content)
        return f
    }

    @Test
    fun `two devices converge and conflicts are preserved`(@TempDir tmp: Path) = runBlocking {
        assumeTrue(RcloneBinary.isAvailable(), "rclone binary not available; skipping")

        val remote = tmp.resolve("remote")
        val srcDir = tmp.resolve("src")
        val target = StorageTarget(UUID.randomUUID().toString(), "Shared", TargetKind.LOCAL_FOLDER, remote.toString())

        val v1 = "version one".toByteArray()
        val v2 = "version two from A".toByteArray()
        val v3 = "version three from B".toByteArray()

        RcloneDaemon.start(configPath = tmp.resolve("rclone.conf")).use { daemon ->
            val engine = RcloneStorageEngine(daemon.rc)
            val sync = SyncEngine(engine)

            // Device A: create vault, add note.txt = v1, push to the shared remote.
            val vaultADir = tmp.resolve("deviceA")
            val vaultA: Vault = VaultFactory.create(vaultADir, password, testKdf, deviceId = "A").vault
            vaultA.addFile(writeSource(srcDir, v1), "note.txt")
            sync.sync(vaultA, target, tmp.resolve("stagingA"))

            // Device B: join by pulling the vault, then unlock as device "B".
            val vaultBDir = tmp.resolve("deviceB")
            engine.pull(target, vaultBDir)
            val vaultB: Vault = VaultFactory.unlockWithPassword(vaultBDir, password, deviceId = "B")
            assertEquals(1, vaultB.list().size)

            // Concurrent edits to the same file on both devices.
            vaultA.addFile(writeSource(srcDir, v2), "note.txt")
            vaultB.addFile(writeSource(srcDir, v3), "note.txt")

            // A syncs first (fast-forward over the original) — no conflict.
            val aOutcome = sync.sync(vaultA, target, tmp.resolve("stagingA"))
            assertTrue(aOutcome.conflicts.isEmpty(), "A should fast-forward, not conflict")

            // B syncs against A's update — true concurrent edit -> one conflict copy.
            val bOutcome = sync.sync(vaultB, target, tmp.resolve("stagingB"))
            assertEquals(1, bOutcome.conflicts.size, "B must detect exactly one conflict")
            val copyPath = bOutcome.conflicts.single().conflictCopyPath
            assertTrue(copyPath.startsWith("note (conflict A "), "conflict labelled with peer device: $copyPath")

            // No data loss: B keeps its own v3 at note.txt; A's v2 survives as the conflict copy.
            vaultB.exportFile("note.txt", tmp.resolve("b-note.bin")).also {
                assertContentEquals(v3, tmp.resolve("b-note.bin").readBytes())
            }
            vaultB.exportFile(copyPath, tmp.resolve("b-copy.bin"))
            assertContentEquals(v2, tmp.resolve("b-copy.bin").readBytes())

            // A syncs again and converges (adopts B's note.txt=v3 and the conflict copy) with no new conflict.
            val aOutcome2 = sync.sync(vaultA, target, tmp.resolve("stagingA"))
            assertTrue(aOutcome2.conflicts.isEmpty(), "A converges without re-conflicting")
            assertEquals(2, vaultA.list().size, "A now has the file plus the conflict copy")
            vaultA.exportFile("note.txt", tmp.resolve("a-note.bin"))
            assertContentEquals(v3, tmp.resolve("a-note.bin").readBytes())

            vaultA.close(); vaultB.close()
        }
    }

    @Test
    fun `deletion propagates between devices`(@TempDir tmp: Path) = runBlocking {
        assumeTrue(RcloneBinary.isAvailable(), "rclone binary not available; skipping")

        val remote = tmp.resolve("remote")
        val srcDir = tmp.resolve("src")
        val target = StorageTarget(UUID.randomUUID().toString(), "Shared", TargetKind.LOCAL_FOLDER, remote.toString())

        RcloneDaemon.start(configPath = tmp.resolve("rclone.conf")).use { daemon ->
            val engine = RcloneStorageEngine(daemon.rc)
            val sync = SyncEngine(engine)

            val vaultA = VaultFactory.create(tmp.resolve("A"), password, testKdf, deviceId = "A").vault
            vaultA.addFile(writeSource(srcDir, "keep me".toByteArray()), "note.txt")
            sync.sync(vaultA, target, tmp.resolve("stagingA"))

            engine.pull(target, tmp.resolve("B"))
            val vaultB = VaultFactory.unlockWithPassword(tmp.resolve("B"), password, deviceId = "B")
            assertEquals(1, vaultB.list().size)

            // A deletes the file and syncs; B should see it gone after syncing.
            vaultA.removeFile("note.txt")
            sync.sync(vaultA, target, tmp.resolve("stagingA"))
            sync.sync(vaultB, target, tmp.resolve("stagingB"))
            assertEquals(0, vaultB.list().size, "deletion must propagate to the peer")

            vaultA.close(); vaultB.close()
        }
    }
}
