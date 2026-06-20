package dev.vaultmesh.vault

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class VaultTreeTest {

    private fun file(path: String) = FileEntry(path = path, isDir = false, size = 1, chunkIds = listOf("x"))
    private fun dir(path: String) = FileEntry(path = path, isDir = true)

    private val entries = listOf(
        file("Photos/a.jpg"),
        file("Photos/2024/c.jpg"),
        file("notes.txt"),
        dir("Empty"),
        file("Photos/b.jpg"),
    )

    @Test
    fun `root lists folders first then files, sorted`() {
        val kids = VaultTree.children(entries, "")
        assertEquals(
            listOf("Empty" to true, "Photos" to true, "notes.txt" to false),
            kids.map { it.name to it.isDir },
        )
    }

    @Test
    fun `nested folder lists its immediate children`() {
        val kids = VaultTree.children(entries, "Photos")
        assertEquals(
            listOf("2024" to true, "a.jpg" to false, "b.jpg" to false),
            kids.map { it.name to it.isDir },
        )
        assertEquals("Photos/2024", kids.first { it.name == "2024" }.path)
        assertEquals("Photos/a.jpg", kids.first { it.name == "a.jpg" }.path)
    }

    @Test
    fun `implicit folders have no backing entry, explicit dirs do`() {
        val root = VaultTree.children(entries, "")
        assertEquals(null, root.first { it.name == "Photos" }.entry) // implicit
        assertEquals("Empty", root.first { it.name == "Empty" }.entry?.path) // explicit
    }

    @Test
    fun `deleted entries are ignored`() {
        val list = listOf(file("keep.txt"), file("gone.txt").copy(deleted = true))
        assertEquals(listOf("keep.txt"), VaultTree.children(list, "").map { it.name })
    }

    @Test
    fun `descendantFiles counts only files under a dir`() {
        assertEquals(3, VaultTree.descendantFiles(entries, "Photos").size)
        assertEquals(4, VaultTree.descendantFiles(entries, "").size)
    }
}
