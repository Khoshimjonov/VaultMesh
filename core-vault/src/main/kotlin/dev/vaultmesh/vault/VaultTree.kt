package dev.vaultmesh.vault

/**
 * Derives a navigable folder tree from the flat list of manifest entries. Folders are mostly
 * *implicit* — they exist because some file's path lives under them — but explicit (possibly empty)
 * folder entries (`isDir = true`) are honored too. Pure and unit-tested; no I/O.
 */
object VaultTree {

    /** One row in a directory listing: a subfolder or a file. */
    data class Node(
        val name: String,
        /** Full vault path of this node ('/'-separated, no leading/trailing slash). */
        val path: String,
        val isDir: Boolean,
        /** The backing file entry for files (and explicit dir entries); null for implicit folders. */
        val entry: FileEntry?,
    )

    /**
     * Immediate children of [dir] (use "" for the vault root). Folders come first, then files, each
     * group sorted case-insensitively by name. [entries] should be the live (non-deleted) entries.
     */
    fun children(entries: List<FileEntry>, dir: String): List<Node> {
        val base = dir.trim('/')
        val prefix = if (base.isEmpty()) "" else "$base/"

        val folderNames = LinkedHashMap<String, FileEntry?>() // name -> explicit dir entry (or null)
        val files = ArrayList<Node>()

        for (e in entries) {
            if (e.deleted) continue
            if (base.isNotEmpty() && !e.path.startsWith(prefix)) continue
            if (e.path == base) continue
            val rel = e.path.removePrefix(prefix)
            if (rel.isEmpty()) continue
            val slash = rel.indexOf('/')
            if (slash >= 0) {
                // Lives inside a subfolder of [dir].
                val name = rel.substring(0, slash)
                folderNames.putIfAbsent(name, null)
            } else if (e.isDir) {
                folderNames[rel] = e // explicit folder directly here
            } else {
                files += Node(rel, e.path, isDir = false, entry = e)
            }
        }

        val folders = folderNames.entries.map { (name, entry) ->
            Node(name, "$prefix$name", isDir = true, entry = entry)
        }
        return folders.sortedBy { it.name.lowercase() } + files.sortedBy { it.name.lowercase() }
    }

    /** Every live file path (not folders) at or under [dir]. Used to aggregate folder sync state. */
    fun descendantFiles(entries: List<FileEntry>, dir: String): List<FileEntry> {
        val base = dir.trim('/')
        val prefix = if (base.isEmpty()) "" else "$base/"
        return entries.filter { !it.deleted && !it.isDir && (base.isEmpty() || it.path.startsWith(prefix)) }
    }
}
