package dev.vaultmesh.storage.rclone

import kotlinx.coroutines.delay
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.util.Base64
import kotlin.io.path.readText

/**
 * Supervises a local `rclone rcd` process and exposes a typed [RcloneRc] client bound to it.
 * Bound to 127.0.0.1 on a random free port with random Basic-auth credentials, so no other
 * local process can drive it.
 */
class RcloneDaemon private constructor(
    private val process: Process,
    val rc: RcloneRc,
    private val logFile: Path,
) : AutoCloseable {

    override fun close() {
        process.destroy()
        if (process.isAlive) {
            process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)
            if (process.isAlive) process.destroyForcibly()
        }
        runCatching { Files.deleteIfExists(logFile) }
    }

    companion object {
        private val random = SecureRandom()

        suspend fun start(
            binary: Path = RcloneBinary.locate(),
            configPath: Path? = null,
        ): RcloneDaemon {
            val port = freePort()
            val user = "vaultmesh"
            val pass = randomHex(24)

            val args = mutableListOf(
                binary.toString(), "rcd",
                "--rc-addr", "127.0.0.1:$port",
                "--rc-user", user,
                "--rc-pass", pass,
            )
            configPath?.let {
                Files.createDirectories(it.parent)
                args += listOf("--config", it.toString())
            }

            val logFile = Files.createTempFile("vaultmesh-rclone-rcd", ".log")
            val process = ProcessBuilder(args)
                .redirectErrorStream(true)
                .redirectOutput(logFile.toFile())
                .start()

            val auth = "Basic " + Base64.getEncoder().encodeToString("$user:$pass".toByteArray())
            val rc = RcloneRc("http://127.0.0.1:$port", auth)

            // Poll until the daemon answers, the process dies, or we time out.
            repeat(100) {
                if (!process.isAlive) {
                    error("rclone rcd exited early: ${runCatching { logFile.readText() }.getOrDefault("")}")
                }
                if (runCatching { rc.version() }.isSuccess) {
                    return RcloneDaemon(process, rc, logFile)
                }
                delay(150)
            }
            process.destroyForcibly()
            error("rclone rcd did not become ready in time")
        }

        private fun freePort(): Int = ServerSocket(0).use { it.localPort }

        private fun randomHex(bytes: Int): String =
            ByteArray(bytes).also { random.nextBytes(it) }.joinToString("") { "%02x".format(it) }
    }
}
