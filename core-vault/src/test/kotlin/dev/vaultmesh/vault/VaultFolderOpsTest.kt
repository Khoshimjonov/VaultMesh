package dev.vaultmesh.vault

import dev.vaultmesh.crypto.KdfParams
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VaultFolderOpsTest {

    private val password = "open-sesame-please".toCharArray()
    private val kdf: (ByteArray) -> KdfParams = { KdfParams.forTesting(it) }

    private fun seed(tmp: Path): Path {
        val src = tmp.resolve("src/Photos").also { it.createDirectories() }
        src.resolve("a.jpg").writeBytes("a".toByteArray())
        src.resolve("b.jpg").writeBytes("bb".toByteArray())
        tmp.resolve("src/Photos/2024").createDirectories()
        tmp.resolve("src/Photos/2024/c.jpg").writeBytes("ccc".toByteArray())
        return tmp.resolve("src/Photos")
    }

    @Test
    fun `createFolder persists an empty folder`(@TempDir tmp: Path) {
        val root = tmp.resolve("v")
        VaultFactory.create(root, password, kdf).vault.use { v ->
            v.createFolder("Documents")
            assertTrue(v.list().any { it.path == "Documents" && it.isDir })
            v.createFolder("Documents") // idempotent
            assertEquals(1, v.list().count { it.path == "Documents" })
        }
        VaultFactory.unlockWithPassword(root, password).use { v ->
            assertTrue(v.list().any { it.path == "Documents" && it.isDir }, "empty folder should survive reopen")
        }
    }

    @Test
    fun `move renames a file and keeps its content`(@TempDir tmp: Path) {
        val root = tmp.resolve("v")
        val src = seed(tmp)
        VaultFactory.create(root, password, kdf).vault.use { v ->
            v.addFolder(src, "Photos")
            v.move("Photos/a.jpg", "Photos/renamed.jpg")
            val live = v.list().map { it.path }.toSet()
            assertTrue("Photos/renamed.jpg" in live)
            assertTrue("Photos/a.jpg" !in live)
            val out = tmp.resolve("out.jpg")
            v.exportFile("Photos/renamed.jpg", out)
            assertContentEquals("a".toByteArray(), out.readBytes())
        }
    }

    @Test
    fun `move relocates a whole folder subtree`(@TempDir tmp: Path) {
        val root = tmp.resolve("v")
        val src = seed(tmp)
        VaultFactory.create(root, password, kdf).vault.use { v ->
            v.addFolder(src, "Photos")
            v.move("Photos/2024", "Archive/2024")
            val live = v.list().map { it.path }.toSet()
            assertTrue("Archive/2024/c.jpg" in live)
            assertTrue("Photos/2024/c.jpg" !in live)
            // Untouched siblings stay put.
            assertTrue("Photos/a.jpg" in live)
        }
    }

    @Test
    fun `removeFolder tombstones the whole subtree`(@TempDir tmp: Path) {
        val root = tmp.resolve("v")
        val src = seed(tmp)
        VaultFactory.create(root, password, kdf).vault.use { v ->
            v.addFolder(src, "Photos")
            v.removeFolder("Photos/2024")
            val live = v.list().map { it.path }.toSet()
            assertTrue("Photos/2024/c.jpg" !in live)
            assertTrue("Photos/a.jpg" in live)
            assertNull(v.list().firstOrNull { it.path.startsWith("Photos/2024") })
        }
    }
}
