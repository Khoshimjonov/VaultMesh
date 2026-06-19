package dev.vaultmesh.storage.rclone

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import kotlin.io.path.isExecutable

/**
 * Locates the rclone executable. Resolution order:
 *   1. -Dvaultmesh.rclone.path=...               (tests / overrides)
 *   2. $VAULTMESH_RCLONE                          (env override)
 *   3. compose.application.resources.dir/rclone  (bundled inside the packaged app)
 *   4. ~/.vaultmesh/bin/rclone[.exe]             (app-managed location)
 *   5. `rclone` on the system PATH
 */
object RcloneBinary {

    class NotFoundException : RuntimeException(
        "rclone executable not found. The packaged app bundles it; for a dev run set " +
            "-Dvaultmesh.rclone.path or \$VAULTMESH_RCLONE, place it at ~/.vaultmesh/bin/rclone, " +
            "or install it on PATH.",
    )

    fun locate(): Path {
        val exe = if (isWindows()) "rclone.exe" else "rclone"

        System.getProperty("vaultmesh.rclone.path")?.let { return ensureExecutable(verify(Paths.get(it))) }
        System.getenv("VAULTMESH_RCLONE")?.let { return ensureExecutable(verify(Paths.get(it))) }

        // Bundled inside the installed app (Compose sets this property at runtime). Copy it out to a
        // writable, executable location so it works even when the app is installed read-only.
        System.getProperty("compose.application.resources.dir")?.let { dir ->
            val bundled = Paths.get(dir, exe)
            if (Files.isRegularFile(bundled)) return materialize(bundled, exe)
        }

        val managed = managedPath(exe)
        if (Files.isRegularFile(managed)) return ensureExecutable(managed)

        onPath(exe)?.let { return it }
        throw NotFoundException()
    }

    private fun vaultmeshHome(): Path =
        System.getProperty("vaultmesh.home")?.let { Paths.get(it) }
            ?: Paths.get(System.getProperty("user.home"), ".vaultmesh")

    private fun managedPath(exe: String): Path = vaultmeshHome().resolve("bin").resolve(exe)

    /** Copies the bundled binary into ~/.vaultmesh/bin (once) and returns the executable copy. */
    private fun materialize(bundled: Path, exe: String): Path {
        val target = managedPath(exe)
        return try {
            val stale = !Files.isRegularFile(target) || Files.size(target) != Files.size(bundled)
            if (stale) {
                Files.createDirectories(target.parent)
                Files.copy(bundled, target, StandardCopyOption.REPLACE_EXISTING)
            }
            ensureExecutable(target)
        } catch (e: Exception) {
            ensureExecutable(bundled) // fall back to in-place (works if the app dir is writable)
        }
    }

    private fun ensureExecutable(path: Path): Path {
        runCatching { path.toFile().setExecutable(true) }
        return path
    }

    fun isAvailable(): Boolean = runCatching { locate() }.isSuccess

    private fun verify(path: Path): Path {
        if (!Files.isRegularFile(path)) throw NotFoundException()
        return path
    }

    private fun isWindows(): Boolean =
        System.getProperty("os.name").lowercase().contains("win")

    private fun onPath(exe: String): Path? =
        System.getenv("PATH")?.split(java.io.File.pathSeparator)?.firstNotNullOfOrNull { dir ->
            val candidate = Paths.get(dir, exe)
            if (Files.isRegularFile(candidate) && candidate.isExecutable()) candidate else null
        }
}
