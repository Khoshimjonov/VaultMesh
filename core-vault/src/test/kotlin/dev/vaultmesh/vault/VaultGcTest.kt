package dev.vaultmesh.vault

import dev.vaultmesh.crypto.KdfParams
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Garbage collection: deleting/editing files leaves orphaned encrypted objects, and collectGarbage()
 * reclaims exactly those while keeping every object a live file still references (dedup-safe).
 */
class VaultGcTest {

    private val testKdf: (ByteArray) -> KdfParams = { KdfParams.forTesting(it) }

    private fun newVault(tmp: Path) =
        VaultFactory.create(tmp.resolve("vault"), "pw".toCharArray(), testKdf, deviceId = "A").vault

    private fun src(tmp: Path, name: String, bytes: ByteArray): Path =
        tmp.resolve(name).also { it.writeBytes(bytes) }

    private fun objectCount(vault: Vault): Long =
        Files.list(vault.root.resolve("objects")).use { s -> s.filter { Files.isRegularFile(it) && !it.fileName.toString().endsWith(".tmp") }.count() }

    @Test
    fun `deleted file's objects are reclaimed`(@TempDir tmp: Path) {
        newVault(tmp).use { v ->
            v.addFile(src(tmp, "a.bin", ByteArray(3000) { 1 }), "a.bin")
            assertEquals(1, objectCount(v))

            v.removeFile("a.bin")
            // Tombstone present, but the encrypted object is still on disk until GC runs.
            assertEquals(1, objectCount(v))

            val res = v.collectGarbage()
            assertEquals(1, res.removedObjects)
            assertTrue(res.bytesFreed > 0)
            assertEquals(0, objectCount(v))
        }
    }

    @Test
    fun `shared chunks survive until the last referrer is gone`(@TempDir tmp: Path) {
        newVault(tmp).use { v ->
            val content = ByteArray(2048) { 7 }
            v.addFile(src(tmp, "a.bin", content), "a.bin")
            v.addFile(src(tmp, "b.bin", content), "b.bin") // identical content dedups to one object
            assertEquals(1, objectCount(v))

            v.removeFile("a.bin")
            assertEquals(0, v.collectGarbage().removedObjects, "object still referenced by b.bin")
            assertEquals(1, objectCount(v))

            v.removeFile("b.bin")
            assertEquals(1, v.collectGarbage().removedObjects)
            assertEquals(0, objectCount(v))
        }
    }

    @Test
    fun `editing a file orphans the old chunk and GC keeps the file readable`(@TempDir tmp: Path) {
        newVault(tmp).use { v ->
            v.addFile(src(tmp, "v1", ByteArray(1500) { 1 }), "note.bin")
            val v2 = ByteArray(1500) { 2 }
            v.addFile(src(tmp, "v2", v2), "note.bin") // same path, new content
            assertEquals(2, objectCount(v), "old + new chunk both on disk")

            val res = v.collectGarbage()
            assertEquals(1, res.removedObjects, "only the orphaned old chunk is swept")
            assertEquals(1, objectCount(v))

            val out = tmp.resolve("out.bin")
            v.exportFile("note.bin", out)
            assertContentEquals(v2, Files.readAllBytes(out), "current version still decrypts after GC")
        }
    }

    @Test
    fun `garbageStats is a dry run`(@TempDir tmp: Path) {
        newVault(tmp).use { v ->
            v.addFile(src(tmp, "a.bin", ByteArray(1000) { 9 }), "a.bin")
            v.removeFile("a.bin")

            val stats = v.garbageStats()
            assertEquals(1, stats.removedObjects)
            assertTrue(stats.bytesFreed > 0)
            assertEquals(1, objectCount(v), "dry run must not delete anything")

            assertEquals(stats.removedObjects, v.collectGarbage().removedObjects)
        }
    }
}
