package dev.vaultmesh.vault

import dev.vaultmesh.crypto.KdfParams
import dev.vaultmesh.crypto.UnlockedVault
import dev.vaultmesh.crypto.VaultCrypto
import dev.vaultmesh.crypto.VaultHeader
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.outputStream
import kotlin.io.path.walk
import kotlin.io.path.writeText

/**
 * A local, on-disk encrypted vault. Layout on disk (mirrored verbatim to every remote later):
 *
 *   <root>/vault.json     public-safe header (wrapped keys + KDF params)
 *   <root>/objects/<id>   encrypted content chunks, content-addressed
 *   <root>/manifest.enc   encrypted file-tree index
 *
 * Files added to the vault are split into chunks, each chunk keyed by HMAC(chunkIdKey, plaintext)
 * so identical chunks dedup, then stored encrypted under that id. Nothing readable touches disk
 * outside [exportFile].
 */
class Vault internal constructor(
    val root: Path,
    initialHeader: VaultHeader,
    private val unlocked: UnlockedVault,
    val deviceId: String = "local",
) : AutoCloseable {

    var header: VaultHeader = initialHeader
        private set

    private val objectsDir: Path = root.resolve("objects")
    private val manifestFile: Path = root.resolve("manifest.enc")
    private val json = Json { ignoreUnknownKeys = true }

    private val chunkIdKey: ByteArray = unlocked.subKey(CHUNK_ID_INFO)

    var manifest: Manifest = loadManifest()
        private set

    // ---- Public operations -------------------------------------------------

    /** Recursively ingest a folder, encrypting every file. Returns number of files added. */
    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    fun addFolder(source: Path, vaultPrefix: String = source.name): Int {
        require(source.isDirectory()) { "$source is not a directory" }
        var count = 0
        source.walk().forEach { p ->
            if (p.isDirectory()) return@forEach
            val rel = source.relativize(p).toString().replace('\\', '/')
            addFile(p, "$vaultPrefix/$rel")
            count++
        }
        return count
    }

    /** Result of reconciling a linked folder: counts of newly added, updated, and removed files. */
    data class SourceSync(val added: Int, val updated: Int, val removed: Int) {
        val changed: Int get() = added + updated + removed
    }

    /**
     * Reconciles a linked source folder into the vault under [vaultPrefix]: ingests new/changed files
     * (detected by size + mtime), skips unchanged ones, and tombstones files that disappeared from the
     * source. This is what keeps a "linked folder" mirrored as it changes on disk.
     */
    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    fun syncFromSource(source: Path, vaultPrefix: String): SourceSync {
        require(source.isDirectory()) { "$source is not a directory" }
        var added = 0
        var updated = 0
        val seen = HashSet<String>()
        source.walk().forEach { p ->
            if (p.isDirectory()) return@forEach
            val rel = source.relativize(p).toString().replace('\\', '/')
            val vaultPath = normalize("$vaultPrefix/$rel")
            seen += vaultPath
            val attrs = Files.readAttributes(p, java.nio.file.attribute.BasicFileAttributes::class.java)
            val existing = manifest.entries.firstOrNull { it.path == vaultPath && !it.deleted }
            if (existing != null && existing.size == attrs.size() && existing.mtimeEpochMs == attrs.lastModifiedTime().toMillis()) {
                return@forEach // unchanged
            }
            addFile(p, vaultPath)
            if (existing == null) added++ else updated++
        }
        // Tombstone vault files under this prefix that no longer exist in the source.
        val prefix = normalize(vaultPrefix)
        val removedPaths = manifest.entries
            .filter { !it.deleted && (it.path == prefix || it.path.startsWith("$prefix/")) && it.path !in seen }
            .map { it.path }
        removedPaths.forEach { removeFile(it) }
        return SourceSync(added, updated, removedPaths.size)
    }

    /** Encrypt and store a single file under [vaultPath]. */
    fun addFile(source: Path, vaultPath: String) {
        val chunkIds = ArrayList<String>()
        source.inputStream().buffered().use { ins ->
            val buf = ByteArray(CHUNK_SIZE)
            while (true) {
                val n = ins.readNBytes(buf, 0, CHUNK_SIZE)
                if (n == 0) break
                val chunk = if (n == CHUNK_SIZE) buf else buf.copyOf(n)
                chunkIds.add(putChunk(chunk))
                if (n < CHUNK_SIZE) break
            }
        }
        val attrs = Files.readAttributes(source, java.nio.file.attribute.BasicFileAttributes::class.java)
        val norm = normalize(vaultPath)
        val priorVector = manifest.entries.firstOrNull { it.path == norm }?.versionVector ?: emptyMap()
        manifest = manifest.upsert(
            FileEntry(
                path = norm,
                isDir = false,
                size = attrs.size(),
                mtimeEpochMs = attrs.lastModifiedTime().toMillis(),
                chunkIds = chunkIds,
                versionVector = VV.bump(priorVector, deviceId),
            ),
        )
        saveManifest()
    }

    /** Marks a file deleted (tombstone) so the deletion propagates to peers. */
    fun removeFile(vaultPath: String) {
        val norm = normalize(vaultPath)
        val entry = manifest.entries.firstOrNull { it.path == norm && !it.deleted } ?: return
        manifest = manifest.upsert(
            entry.copy(
                deleted = true,
                chunkIds = emptyList(),
                size = 0,
                conflictOf = null,
                versionVector = VV.bump(entry.versionVector, deviceId),
            ),
        )
        saveManifest()
    }

    /**
     * Resolves a conflict copy: if [keepConflictVersion] its content replaces the original file;
     * either way the conflict copy is tombstoned. The choice propagates to peers like any edit.
     */
    fun resolveConflict(conflictPath: String, keepConflictVersion: Boolean) {
        val norm = normalize(conflictPath)
        val copy = manifest.entries.firstOrNull { it.path == norm && it.conflictOf != null } ?: return
        val originalPath = copy.conflictOf!!
        if (keepConflictVersion) {
            val original = manifest.entries.firstOrNull { it.path == originalPath }
            val baseVector = VV.merge(copy.versionVector, original?.versionVector ?: emptyMap())
            manifest = manifest.upsert(
                FileEntry(
                    path = originalPath,
                    isDir = false,
                    size = copy.size,
                    mtimeEpochMs = System.currentTimeMillis(),
                    chunkIds = copy.chunkIds,
                    versionVector = VV.bump(baseVector, deviceId),
                ),
            )
        }
        manifest = manifest.upsert(
            copy.copy(
                deleted = true,
                chunkIds = emptyList(),
                size = 0,
                conflictOf = null,
                versionVector = VV.bump(copy.versionVector, deviceId),
            ),
        )
        saveManifest()
    }

    /** Re-wraps the VMK under a new password (content is untouched) and persists the header. */
    fun changePassword(newPassword: CharArray, kdfFactory: (ByteArray) -> KdfParams = { KdfParams.production(it) }) {
        header = VaultCrypto.changePassword(header, unlocked, newPassword, kdfFactory)
        persistHeader()
    }

    /** Issues a fresh recovery key (invalidating the old one) and persists the header. */
    fun regenerateRecoveryKey(kdfFactory: (ByteArray) -> KdfParams = { KdfParams.production(it) }): String {
        val (newHeader, recoveryKey) = VaultCrypto.regenerateRecoveryKey(header, unlocked, kdfFactory)
        header = newHeader
        persistHeader()
        return recoveryKey
    }

    private fun persistHeader() {
        root.resolve("vault.json").writeText(VaultCrypto.serializeHeader(header))
    }

    /** Decrypt a stored file out to [dest]. */
    fun exportFile(vaultPath: String, dest: Path) {
        val entry = manifest.entries.firstOrNull { it.path == normalize(vaultPath) && !it.isDir && !it.deleted }
            ?: error("No such file in vault: $vaultPath")
        dest.parent?.let { Files.createDirectories(it) }
        dest.outputStream().buffered().use { out ->
            for (id in entry.chunkIds) {
                objectPath(id).inputStream().use { ins ->
                    unlocked.contentCipher().decryptingStream(ins, id.toByteArray()).use { dec ->
                        dec.copyTo(out)
                    }
                }
            }
        }
    }

    fun list(): List<FileEntry> = manifest.entries.filterNot { it.deleted }.sortedBy { it.path }

    // ---- Sync support ------------------------------------------------------

    /**
     * Decrypts a peer's `manifest.enc` using THIS vault's key. Valid because every device of the
     * same vault shares the master key — that's how two devices read each other's manifests.
     */
    fun decryptManifest(manifestEncFile: Path): Manifest {
        val bytes = manifestEncFile.inputStream().use { ins ->
            unlocked.contentCipher().decryptingStream(ins, MANIFEST_AAD).use { it.readBytes() }
        }
        return json.decodeFromString(Manifest.serializer(), bytes.decodeToString())
    }

    /** Copies any objects not already present from another vault's `objects/` dir (content-addressed, safe). */
    fun importObjects(fromObjectsDir: Path): Int {
        if (!Files.isDirectory(fromObjectsDir)) return 0
        Files.createDirectories(objectsDir)
        var copied = 0
        Files.list(fromObjectsDir).use { stream ->
            stream.forEach { src ->
                val name = src.fileName.toString()
                if (Files.isRegularFile(src) && !name.endsWith(".tmp")) {
                    val dst = objectsDir.resolve(name)
                    if (!dst.exists()) { Files.copy(src, dst); copied++ }
                }
            }
        }
        return copied
    }

    /** Replaces the manifest with a merged one and persists it. */
    fun applyMergedManifest(merged: Manifest) {
        manifest = merged
        saveManifest()
    }

    override fun close() = unlocked.close()

    // ---- Internals ---------------------------------------------------------

    /** Store one plaintext chunk encrypted; returns its content-addressed id. Dedups existing ids. */
    private fun putChunk(chunk: ByteArray): String {
        val id = chunkId(chunk)
        val target = objectPath(id)
        if (target.exists()) return id // dedup
        Files.createDirectories(objectsDir)
        val tmp = objectsDir.resolve("$id.tmp")
        tmp.outputStream().use { fos ->
            unlocked.contentCipher().encryptingStream(fos, id.toByteArray()).use { it.write(chunk) }
        }
        Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE)
        return id
    }

    private fun chunkId(plaintext: ByteArray): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(chunkIdKey, "HmacSHA256"))
        return mac.doFinal(plaintext).toHex()
    }

    private fun objectPath(id: String): Path = objectsDir.resolve(id)

    private fun loadManifest(): Manifest {
        if (!manifestFile.exists()) return Manifest()
        val bytes = manifestFile.inputStream().use { ins ->
            unlocked.contentCipher().decryptingStream(ins, MANIFEST_AAD).use { it.readBytes() }
        }
        return json.decodeFromString(Manifest.serializer(), bytes.decodeToString())
    }

    private fun saveManifest() {
        val bytes = json.encodeToString(Manifest.serializer(), manifest).toByteArray()
        val tmp = root.resolve("manifest.enc.tmp")
        tmp.outputStream().use { fos ->
            unlocked.contentCipher().encryptingStream(fos, MANIFEST_AAD).use { it.write(bytes) }
        }
        Files.move(tmp, manifestFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    private fun normalize(path: String): String = path.trim('/').replace('\\', '/')

    companion object {
        const val CHUNK_SIZE = 1 shl 20 // 1 MiB (fixed-size MVP; FastCDC is a later upgrade)
        private const val CHUNK_ID_INFO = "vaultmesh:chunkid:v1"
        private val MANIFEST_AAD = "vaultmesh:manifest".toByteArray()

        private fun ByteArray.toHex(): String =
            joinToString("") { "%02x".format(it) }
    }
}
