package dev.vaultmesh.app

import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import java.nio.file.StandardWatchEventKinds.ENTRY_DELETE
import java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY
import java.nio.file.WatchService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.io.path.isDirectory

/**
 * Best-effort recursive folder watcher. Coalesces filesystem events and invokes [onChange] after a
 * short quiet period. It's an immediacy optimization only — the periodic sync poll re-reconciles
 * linked folders regardless, so a missed event just means a few minutes' latency, never data loss.
 */
class FolderWatcher(private val onChange: () -> Unit) : AutoCloseable {

    private var watchService: WatchService? = null
    private var worker: Thread? = null
    private val running = AtomicBoolean(false)

    fun watch(roots: List<Path>) {
        close()
        if (roots.isEmpty()) return
        val service = FileSystems.getDefault().newWatchService()
        watchService = service
        roots.forEach { runCatching { registerAll(it, service) } }
        running.set(true)
        worker = thread(isDaemon = true, name = "vaultmesh-folder-watcher") {
            var pendingSince = 0L
            while (running.get()) {
                val key = runCatching { service.poll(400, TimeUnit.MILLISECONDS) }.getOrNull()
                if (key != null) {
                    for (event in key.pollEvents()) {
                        val dir = key.watchable() as? Path
                        val name = event.context() as? Path
                        if (dir != null && name != null) {
                            val child = dir.resolve(name)
                            if (child.isDirectory()) runCatching { registerAll(child, service) }
                        }
                    }
                    key.reset()
                    pendingSince = System.currentTimeMillis()
                }
                if (pendingSince > 0 && System.currentTimeMillis() - pendingSince > 800) {
                    pendingSince = 0
                    runCatching { onChange() }
                }
            }
        }
    }

    private fun registerAll(root: Path, service: WatchService) {
        if (!root.isDirectory()) return
        Files.walk(root).use { stream ->
            stream.filter { it.isDirectory() }.forEach { dir ->
                runCatching { dir.register(service, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY) }
            }
        }
    }

    override fun close() {
        running.set(false)
        worker?.interrupt()
        worker = null
        runCatching { watchService?.close() }
        watchService = null
    }
}
