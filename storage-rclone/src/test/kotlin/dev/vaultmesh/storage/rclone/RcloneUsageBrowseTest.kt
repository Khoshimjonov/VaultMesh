package dev.vaultmesh.storage.rclone

import dev.vaultmesh.storage.RemoteEntry
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Verifies the storage-inspection calls the capacity readout + cloud explorer use: `operations/about`
 * reports quota for a backend that supports it (the local fs reports disk total/used/free), and
 * `operations/list` browses a directory tree by sub-path. Uses the local backend so no creds/cloud.
 */
class RcloneUsageBrowseTest {

    @Test
    fun `usage reports disk space and list browses by sub-path`(@TempDir tmp: Path) = runBlocking {
        assumeTrue(RcloneBinary.isAvailable(), "rclone binary not available; skipping")

        // A small vault-shaped tree: vault.json + an objects/ folder with one blob.
        val root = tmp.resolve("VaultMesh").also { it.createDirectories() }
        root.resolve("vault.json").writeBytes("{}".toByteArray())
        root.resolve("objects").createDirectories()
        root.resolve("objects").resolve("deadbeef").writeBytes(ByteArray(2048))

        RcloneDaemon.start(configPath = tmp.resolve("rclone.conf")).use { daemon ->
            val engine = RcloneStorageEngine(daemon.rc)

            // about(): local backend always reports total/used/free.
            val usage = engine.usage(root.toString())
            assertNotNull(usage, "local backend should report capacity")
            assertTrue(usage.hasAny)
            assertTrue((usage.total ?: 0) > 0, "total disk size should be positive")
            assertNotNull(usage.free, "free space should be reported")

            // list() at the root: a folder and a file.
            val top = engine.list(root.toString(), "")
            assertEquals(setOf("objects", "vault.json"), top.map(RemoteEntry::name).toSet())
            val objectsDir = top.single { it.name == "objects" }
            assertTrue(objectsDir.isDir)
            val vaultJson = top.single { it.name == "vault.json" }
            assertTrue(!vaultJson.isDir && vaultJson.size == 2L)

            // list() into the objects sub-path returns the blob with its full relative path.
            val objects = engine.list(root.toString(), "objects")
            val blob = objects.single()
            assertEquals("deadbeef", blob.name)
            assertEquals("objects/deadbeef", blob.path)
            assertEquals(2048L, blob.size)
        }
    }
}
