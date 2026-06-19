package dev.vaultmesh.app

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.readLines
import kotlin.io.path.writeText

/** A source folder kept mirrored into the vault under [prefix]. */
data class LinkedFolder(val source: String, val prefix: String)

/** TSV-backed store (sourcePath<TAB>prefix per line) — no serialization dependency needed. */
class LinkedFolderStore(private val file: Path) {

    fun load(): List<LinkedFolder> {
        if (!file.isRegularFile()) return emptyList()
        return file.readLines().mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size == 2 && parts[0].isNotBlank()) LinkedFolder(parts[0], parts[1]) else null
        }
    }

    fun save(items: List<LinkedFolder>) {
        file.parent?.let { Files.createDirectories(it) }
        file.writeText(items.joinToString("\n") { "${it.source}\t${it.prefix}" })
    }
}
