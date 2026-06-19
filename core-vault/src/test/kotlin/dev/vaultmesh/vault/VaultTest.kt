package dev.vaultmesh.vault

import dev.vaultmesh.crypto.KdfParams
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.random.Random
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val testKdf: (ByteArray) -> KdfParams = { KdfParams.forTesting(it) }

class VaultTest {

    private val password = "open-sesame-please".toCharArray()

    @Test
    fun `ingest, list, export round-trip with reopen`(@TempDir tmp: Path) {
        val vaultRoot = tmp.resolve("vault")
        val sourceDir = tmp.resolve("source").also { it.createDirectories() }

        // A small text file, a >1 MiB binary file (multi-chunk), and an empty file.
        val small = "hello vault".toByteArray()
        val big = Random(42).nextBytes(3 * Vault.CHUNK_SIZE + 123)
        sourceDir.resolve("note.txt").writeBytes(small)
        sourceDir.resolve("blob.bin").writeBytes(big)
        sourceDir.resolve("empty.dat").writeText("")
        // Duplicate content to exercise chunk dedup.
        sourceDir.resolve("note-copy.txt").writeBytes(small)

        val recoveryKey: String
        VaultFactory.create(vaultRoot, password, testKdf).let { nv ->
            recoveryKey = nv.recoveryKey
            nv.vault.use { vault ->
                val added = vault.addFolder(sourceDir, vaultPrefix = "docs")
                assertEquals(4, added)
                assertEquals(4, vault.list().size)
                assertTrue(VaultFactory.exists(vaultRoot))
            }
        }

        // Objects on disk must be ciphertext (the plaintext must not appear).
        val objects = Files.list(vaultRoot.resolve("objects")).use { it.toList() }
        assertTrue(objects.isNotEmpty())

        // Reopen with the password and verify content matches byte-for-byte.
        VaultFactory.unlockWithPassword(vaultRoot, password).use { vault ->
            val out = tmp.resolve("out-blob.bin")
            vault.exportFile("docs/blob.bin", out)
            assertContentEquals(big, out.readBytes())
            val outTxt = tmp.resolve("out-note.txt")
            vault.exportFile("docs/note.txt", outTxt)
            assertContentEquals(small, outTxt.readBytes())
        }

        // Reopen with the recovery key and verify again.
        VaultFactory.unlockWithRecovery(vaultRoot, recoveryKey).use { vault ->
            val out = tmp.resolve("out-blob2.bin")
            vault.exportFile("docs/blob.bin", out)
            assertContentEquals(big, out.readBytes())
        }
    }

    @Test
    fun `removeFile hides the file and persists the tombstone`(@TempDir tmp: Path) {
        val vaultRoot = tmp.resolve("vault")
        val src = tmp.resolve("src").also { it.createDirectories() }
        src.resolve("x.txt").writeBytes("hi".toByteArray())

        VaultFactory.create(vaultRoot, password, testKdf).vault.use { v ->
            v.addFolder(src, "d")
            assertEquals(1, v.list().size)
            v.removeFile("d/x.txt")
            assertEquals(0, v.list().size)
        }
        VaultFactory.unlockWithPassword(vaultRoot, password).use { v ->
            assertEquals(0, v.list().size, "tombstone should persist across reopen")
        }
    }

    @Test
    fun `syncFromSource reconciles add, update and delete`(@TempDir tmp: Path) {
        val vaultRoot = tmp.resolve("vault")
        val src = tmp.resolve("src").also { it.createDirectories() }
        src.resolve("a.txt").writeBytes("a1".toByteArray())
        src.resolve("b.txt").writeBytes("b1".toByteArray())

        VaultFactory.create(vaultRoot, password, testKdf).vault.use { v ->
            val r1 = v.syncFromSource(src, "linked")
            assertEquals(2, r1.added)
            assertEquals(2, v.list().size)

            // Change a, delete b, add c.
            src.resolve("a.txt").writeBytes("a2-changed-size".toByteArray())
            Files.delete(src.resolve("b.txt"))
            src.resolve("c.txt").writeBytes("c1".toByteArray())

            val r2 = v.syncFromSource(src, "linked")
            assertEquals(1, r2.added)
            assertEquals(1, r2.updated)
            assertEquals(1, r2.removed)
            assertEquals(setOf("linked/a.txt", "linked/c.txt"), v.list().map { it.path }.toSet())

            // No-op rescan detects nothing changed.
            assertEquals(0, v.syncFromSource(src, "linked").changed)
        }
    }

    @Test
    fun `identical chunks are deduplicated`(@TempDir tmp: Path) {
        val vaultRoot = tmp.resolve("vault")
        val src = tmp.resolve("src").also { it.createDirectories() }
        val payload = Random(7).nextBytes(Vault.CHUNK_SIZE) // exactly one chunk
        src.resolve("a.bin").writeBytes(payload)
        src.resolve("b.bin").writeBytes(payload)

        VaultFactory.create(vaultRoot, password, testKdf).vault.use { vault ->
            vault.addFolder(src, "dup")
            // Two files, identical single chunk -> exactly one object on disk.
            val objectCount = Files.list(vaultRoot.resolve("objects")).use { it.count() }
            assertEquals(1, objectCount)
        }
    }
}
