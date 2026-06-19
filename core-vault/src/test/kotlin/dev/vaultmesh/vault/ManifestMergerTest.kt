package dev.vaultmesh.vault

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ManifestMergerTest {

    private fun entry(path: String, chunk: String, vv: Map<String, Long>) =
        FileEntry(path = path, chunkIds = listOf(chunk), versionVector = vv)

    @Test
    fun `remote-only entry is adopted`() {
        val local = Manifest(entries = emptyList())
        val remote = Manifest(entries = listOf(entry("a.txt", "c1", mapOf("B" to 1))))
        val result = ManifestMerger.merge(local, remote, "A")
        assertEquals(listOf("a.txt"), result.manifest.entries.map { it.path })
        assertTrue(result.conflicts.isEmpty())
    }

    @Test
    fun `remote newer fast-forwards`() {
        val local = Manifest(entries = listOf(entry("a.txt", "c1", mapOf("A" to 1))))
        val remote = Manifest(entries = listOf(entry("a.txt", "c2", mapOf("A" to 1, "B" to 1))))
        val result = ManifestMerger.merge(local, remote, "A")
        val a = result.manifest.entries.single { it.path == "a.txt" }
        assertEquals(listOf("c2"), a.chunkIds) // took remote content
        assertTrue(result.conflicts.isEmpty())
    }

    @Test
    fun `local newer keeps local`() {
        val local = Manifest(entries = listOf(entry("a.txt", "c2", mapOf("A" to 2, "B" to 1))))
        val remote = Manifest(entries = listOf(entry("a.txt", "c1", mapOf("A" to 1, "B" to 1))))
        val result = ManifestMerger.merge(local, remote, "A")
        val a = result.manifest.entries.single { it.path == "a.txt" }
        assertEquals(listOf("c2"), a.chunkIds)
        assertTrue(result.conflicts.isEmpty())
    }

    @Test
    fun `same content merges clocks without conflict`() {
        val local = Manifest(entries = listOf(entry("a.txt", "c1", mapOf("A" to 1))))
        val remote = Manifest(entries = listOf(entry("a.txt", "c1", mapOf("B" to 1))))
        val result = ManifestMerger.merge(local, remote, "A")
        val a = result.manifest.entries.single { it.path == "a.txt" }
        assertTrue(result.conflicts.isEmpty())
        assertEquals(mapOf("A" to 1L, "B" to 1L), a.versionVector)
    }

    @Test
    fun `concurrent different content produces a conflict copy with no data loss`() {
        // A edited to c2 (A:2), B edited to c3 (A:1,B:1) — neither dominates.
        val local = Manifest(entries = listOf(entry("docs/report.txt", "c2", mapOf("A" to 2))))
        val remote = Manifest(entries = listOf(entry("docs/report.txt", "c3", mapOf("A" to 1, "B" to 1))))
        val result = ManifestMerger.merge(local, remote, "A", timestampMs = 0L)

        assertEquals(1, result.conflicts.size)
        val conflict = result.conflicts.single()
        assertEquals("docs/report.txt", conflict.originalPath)
        assertTrue(conflict.conflictCopyPath.startsWith("docs/report (conflict B "))
        assertTrue(conflict.conflictCopyPath.endsWith(".txt"))

        // Original keeps local content; conflict copy carries the remote content. Both survive.
        val original = result.manifest.entries.single { it.path == "docs/report.txt" }
        val copy = result.manifest.entries.single { it.path == conflict.conflictCopyPath }
        assertEquals(listOf("c2"), original.chunkIds)
        assertEquals(listOf("c3"), copy.chunkIds)

        // Winning vectors dominate both originals, so a third sync won't re-conflict.
        assertTrue(VV.dominates(original.versionVector, mapOf("A" to 2)))
        assertTrue(VV.dominates(original.versionVector, mapOf("A" to 1, "B" to 1)))
    }

    @Test
    fun `delete propagates when the tombstone dominates`() {
        val local = Manifest(entries = listOf(entry("a.txt", "c1", mapOf("A" to 1))))
        val remote = Manifest(entries = listOf(FileEntry("a.txt", deleted = true, versionVector = mapOf("A" to 1, "B" to 1))))
        val result = ManifestMerger.merge(local, remote, "A")
        assertTrue(result.manifest.entries.single { it.path == "a.txt" }.deleted)
        assertTrue(result.conflicts.isEmpty())
    }

    @Test
    fun `concurrent delete and edit preserves the edit`() {
        val local = Manifest(entries = listOf(entry("a.txt", "c2", mapOf("A" to 2))))
        val remote = Manifest(entries = listOf(FileEntry("a.txt", deleted = true, versionVector = mapOf("A" to 1, "B" to 1))))
        val result = ManifestMerger.merge(local, remote, "A")
        val a = result.manifest.entries.single { it.path == "a.txt" }
        assertTrue(!a.deleted, "an edit must survive a concurrent delete")
        assertEquals(listOf("c2"), a.chunkIds)
        assertTrue(result.conflicts.isEmpty())
    }

    @Test
    fun `conflict copy records its original path`() {
        val local = Manifest(entries = listOf(entry("a.txt", "c2", mapOf("A" to 2))))
        val remote = Manifest(entries = listOf(entry("a.txt", "c3", mapOf("A" to 1, "B" to 1))))
        val result = ManifestMerger.merge(local, remote, "A", timestampMs = 0L)
        assertEquals("a.txt", result.manifest.entries.single { it.conflictOf != null }.conflictOf)
    }

    @Test
    fun `merge is idempotent after resolution`() {
        val local = Manifest(entries = listOf(entry("a.txt", "c2", mapOf("A" to 2))))
        val remote = Manifest(entries = listOf(entry("a.txt", "c3", mapOf("A" to 1, "B" to 1))))
        val first = ManifestMerger.merge(local, remote, "A", timestampMs = 0L)
        // Re-merging the resolved manifest with the same remote yields no NEW conflicts.
        val second = ManifestMerger.merge(first.manifest, remote, "A", timestampMs = 0L)
        assertTrue(second.conflicts.isEmpty(), "resolved state must not re-conflict")
    }
}
