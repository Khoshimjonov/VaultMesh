package dev.vaultmesh.app

import java.awt.Desktop
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.exists
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.name

/**
 * Opens vault files in their system-associated application. Because everything in the vault is
 * encrypted, a file must be decrypted to a temporary location before another app can read it.
 *
 * - Read-only opens get a non-writable temp copy and are never re-imported.
 * - Editable opens are watched: when the external app saves changes, [pollChanged] reports them so
 *   the caller can re-encrypt the new content back into the vault.
 *
 * All temp copies are plaintext while open, so they live under a single managed directory that is
 * wiped (bytes overwritten, then deleted) when the vault locks or the app exits. The directory is
 * also wiped on startup to clear anything a previous crash may have left behind.
 */
class OpenFilesManager(private val baseDir: Path) {

    data class OpenFile(
        val vaultPath: String,
        val tempFile: Path,
        var lastModified: Long,
        val editable: Boolean,
    )

    private val open = ArrayList<OpenFile>()

    init {
        wipeAll()
    }

    /** True if launching the OS-associated app is supported on this platform. */
    fun canOpen(): Boolean =
        Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)

    /**
     * Decrypts [vaultPath] into a temp file via [decryptTo], opens it in the associated app, and
     * tracks it. The temp file keeps the original name (so the OS picks the right app).
     */
    @Synchronized
    fun open(vaultPath: String, fileName: String, editable: Boolean, decryptTo: (Path) -> Unit): Path {
        check(canOpen()) { "Opening files in another app isn't supported on this system." }
        val dir = Files.createTempDirectory(ensureBase(), "f")
        val temp = dir.resolve(fileName.ifBlank { "file" })
        decryptTo(temp)
        if (!editable) runCatching { temp.toFile().setWritable(false) }
        open += OpenFile(vaultPath, temp, temp.getLastModifiedTime().toMillis(), editable)
        Desktop.getDesktop().open(temp.toFile())
        return temp
    }

    /** Editable temp files whose on-disk content changed since last checked (stamp is advanced). */
    @Synchronized
    fun pollChanged(): List<OpenFile> {
        val changed = ArrayList<OpenFile>()
        for (f in open) {
            if (!f.editable || !f.tempFile.exists()) continue
            val mtime = runCatching { f.tempFile.getLastModifiedTime().toMillis() }.getOrDefault(f.lastModified)
            if (mtime != f.lastModified) {
                f.lastModified = mtime
                changed += f
            }
        }
        return changed
    }

    /** Records the post-reingest mtime so a save we just absorbed doesn't re-trigger. */
    @Synchronized
    fun refreshStamp(vaultPath: String) {
        open.firstOrNull { it.vaultPath == vaultPath }?.let {
            it.lastModified = runCatching { it.tempFile.getLastModifiedTime().toMillis() }.getOrDefault(it.lastModified)
        }
    }

    @Synchronized
    fun wipeAll() {
        open.clear()
        if (!baseDir.exists()) return
        runCatching {
            Files.walk(baseDir).use { stream ->
                stream.sorted(Comparator.reverseOrder<Path>()).forEach { p ->
                    if (Files.isRegularFile(p)) shred(p)
                    runCatching { Files.deleteIfExists(p) }
                }
            }
        }
    }

    private fun ensureBase(): Path = Files.createDirectories(baseDir)

    /** Best-effort overwrite so plaintext doesn't linger in free space before deletion. */
    private fun shred(p: Path) {
        runCatching {
            val len = Files.size(p)
            if (len > 0) {
                Files.newByteChannel(p, StandardOpenOption.WRITE).use { ch ->
                    val zeros = java.nio.ByteBuffer.allocate(8192)
                    var written = 0L
                    while (written < len) {
                        zeros.clear()
                        val n = minOf(zeros.capacity().toLong(), len - written).toInt()
                        zeros.limit(n)
                        written += ch.write(zeros)
                    }
                }
            }
        }
    }
}
