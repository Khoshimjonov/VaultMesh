package dev.vaultmesh.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.vaultmesh.crypto.WrongCredentialsException
import dev.vaultmesh.storage.StorageEngine
import dev.vaultmesh.storage.StorageTarget
import dev.vaultmesh.storage.TargetKind
import dev.vaultmesh.storage.rclone.RcloneBinary
import dev.vaultmesh.storage.rclone.RcloneDaemon
import dev.vaultmesh.storage.rclone.RcloneStorageEngine
import dev.vaultmesh.sync.SyncEngine
import dev.vaultmesh.sync.TargetStore
import dev.vaultmesh.sync.VaultReplicator
import dev.vaultmesh.vault.DeviceId
import dev.vaultmesh.vault.FileEntry
import dev.vaultmesh.vault.Vault
import dev.vaultmesh.vault.VaultFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.nio.file.Paths
import java.util.UUID

enum class Screen { Onboarding, Unlock, RecoveryReveal, Browser, Settings, Help }

/**
 * Holds all app state and bridges the Compose UI to the (already-tested) vault layer.
 * Heavy crypto (Argon2id) runs off the UI thread; state is only mutated on the Swing/Main
 * dispatcher so Compose stays consistent.
 */
class AppViewModel(val vaultRoot: Path) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var vault: Vault? = null

    var screen by mutableStateOf(if (VaultFactory.exists(vaultRoot)) Screen.Unlock else Screen.Onboarding)
        private set
    var busy by mutableStateOf(false)
        private set
    var busyMessage by mutableStateOf("")
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var recoveryKey by mutableStateOf<String?>(null)
        private set
    var files by mutableStateOf<List<FileEntry>>(emptyList())
        private set
    var statusMessage by mutableStateOf<String?>(null)
        private set

    // --- Replication / sync (Phase 2 & 3) ---
    private val homeDir: Path = Paths.get(System.getProperty("user.home"), ".vaultmesh")
    private val targetStore = TargetStore(homeDir.resolve("config").resolve("targets.json"))
    private val settingsStore = AppSettingsStore(homeDir.resolve("config").resolve("settings.json"))
    private val linkedFolderStore = LinkedFolderStore(homeDir.resolve("config").resolve("linked-folders.tsv"))
    private val deviceId: String = DeviceId.loadOrCreate(homeDir.resolve("config").resolve("device-id"))
    private var daemon: RcloneDaemon? = null
    private var engine: StorageEngine? = null

    private var settings = settingsStore.load()
    private var autoSyncJob: Job? = null
    private var pendingSyncJob: Job? = null
    private var folderWatcher: FolderWatcher? = null

    /** Serializes every vault-mutating operation so background sync/watcher can't race manual edits. */
    private val vaultMutex = Mutex()

    /** Ensures only one rclone daemon is ever started, even under concurrent callers. */
    private val engineMutex = Mutex()

    /** Runs [block] against the open vault off the UI thread, holding the vault lock. */
    private suspend fun <T> withVault(block: (Vault) -> T): T? {
        val v = vault ?: return null
        return vaultMutex.withLock { withContext(Dispatchers.Default) { block(v) } }
    }

    var targets by mutableStateOf(targetStore.load())
        private set
    var targetStatus by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    var rcloneAvailable by mutableStateOf(RcloneBinary.isAvailable())
        private set
    var configuredRemotes by mutableStateOf<List<String>>(emptyList())
        private set
    var showConnectDialog by mutableStateOf(false)
        private set
    var autoSyncEnabled by mutableStateOf(settings.autoSyncEnabled)
        private set
    var autoSyncing by mutableStateOf(false)
        private set
    var lastSyncLabel by mutableStateOf<String?>(null)
        private set
    var linkedFolders by mutableStateOf(linkedFolderStore.load())
        private set

    init {
        Runtime.getRuntime().addShutdownHook(Thread { folderWatcher?.close(); daemon?.close() })
    }

    val vaultPath: String get() = vaultRoot.toString()

    fun dismissError() { error = null }

    fun openSettings() { screen = Screen.Settings; refreshRemotes() }
    fun closeSettings() { screen = Screen.Browser }

    private var screenBeforeHelp = Screen.Unlock
    fun openHelp() { screenBeforeHelp = screen; screen = Screen.Help }
    fun closeHelp() { screen = screenBeforeHelp }

    fun addLocalFolderTarget(folder: Path) {
        val target = StorageTarget(
            id = UUID.randomUUID().toString(),
            displayName = folder.fileName?.toString() ?: folder.toString(),
            kind = TargetKind.LOCAL_FOLDER,
            fsRoot = folder.resolve("VaultMesh").toString(),
        )
        updateTargets(targets + target)
    }

    fun addCloudRemoteTarget(remoteName: String) {
        val target = StorageTarget(
            id = UUID.randomUUID().toString(),
            displayName = remoteName,
            kind = TargetKind.RCLONE_REMOTE,
            fsRoot = "$remoteName:VaultMesh",
        )
        updateTargets(targets + target)
    }

    fun openConnectDialog() { showConnectDialog = true; refreshRemotes() }
    fun closeConnectDialog() { showConnectDialog = false }

    /**
     * Connects a provider. OAuth providers open the browser (the call blocks until the user
     * authorizes); credential providers use [fieldValues]. On success the remote is added as a
     * replication target automatically.
     */
    fun connectProvider(provider: Provider, remoteName: String, fieldValues: Map<String, String>) =
        run(
            if (provider.oauth) {
                "A browser window is opening — finish signing in to ${provider.displayName}, then return here."
            } else {
                "Connecting ${provider.displayName}…"
            },
        ) {
            val e = ensureEngine() ?: return@run
            val params = provider.fixedParams + fieldValues.filterValues { it.isNotBlank() }
            withContext(Dispatchers.IO) { e.createRemote(remoteName, provider.type, params) }
            configuredRemotes = runCatching { e.listConfiguredRemotes() }.getOrDefault(configuredRemotes)
            addCloudRemoteTarget(remoteName)
            showConnectDialog = false
            statusMessage = "Connected ${provider.displayName} as \"$remoteName\""
        }

    fun removeTarget(id: String) = updateTargets(targets.filterNot { it.id == id })

    fun setTargetEnabled(id: String, enabled: Boolean) =
        updateTargets(targets.map { if (it.id == id) it.copy(enabled = enabled) else it })

    private fun updateTargets(list: List<StorageTarget>) {
        targets = list
        targetStore.save(list)
    }

    private fun refreshRemotes() = scope.launch {
        val e = ensureEngine() ?: return@launch
        configuredRemotes = runCatching { e.listConfiguredRemotes() }.getOrDefault(emptyList())
    }

    /** One-way push-only mirror (simple backup) to every enabled target. */
    fun replicateNow() = run("Replicating…") {
        val e = ensureEngine() ?: return@run
        targetStatus = targets.associate { it.id to if (it.enabled) "Pushing…" else "Disabled" }
        val results = withContext(Dispatchers.IO) { VaultReplicator(e).replicate(vaultRoot, targets) }
        targetStatus = targetStatus.toMutableMap().apply {
            results.forEach { r -> put(r.target.id, if (r.ok) "Synced" else "Error: ${r.error ?: "failed"}") }
        }
        statusMessage = "Replicated to ${results.count { it.ok }}/${results.size} target(s)"
    }

    /** Two-way sync against every enabled target (manual; shows the progress overlay). */
    fun syncNow() = run("Syncing…") {
        val e = ensureEngine() ?: return@run
        val v = vault ?: return@run
        withVault { reconcileLinkedFoldersSync(it) } // pick up on-disk changes first
        statusMessage = performSync(e, v)
    }

    /** Shared sync routine for manual + background use. Pull, merge (version vectors), push. */
    private suspend fun performSync(e: StorageEngine, v: Vault): String = vaultMutex.withLock {
        val sync = SyncEngine(e)
        val enabled = targets.filter { it.enabled }
        targetStatus = enabled.associate { it.id to "Syncing…" }
        var okCount = 0
        var conflictCount = 0
        for (t in enabled) {
            val staging = homeDir.resolve("staging").resolve(t.id)
            val outcome = withContext(Dispatchers.IO) { sync.sync(v, t, staging) }
            conflictCount += outcome.conflicts.size
            if (outcome.pushedOk) okCount++
            targetStatus = targetStatus + (t.id to when {
                !outcome.pushedOk -> "Error: ${outcome.error ?: "failed"}"
                outcome.conflicts.isEmpty() -> "Synced"
                else -> "Synced (${outcome.conflicts.size} conflict copies)"
            })
        }
        refresh() // sync may have pulled new files or created conflict copies
        lastSyncLabel = "Last synced just now"
        buildString {
            append("Synced $okCount/${enabled.size} target(s)")
            if (conflictCount > 0) append(" — $conflictCount conflict copies created")
        }
    }

    // --- Auto-sync scheduler ---

    fun setAutoSync(enabled: Boolean) {
        autoSyncEnabled = enabled
        settings = settings.copy(autoSyncEnabled = enabled)
        settingsStore.save(settings)
        if (enabled) startAutoSync() else stopAutoSync()
    }

    private fun startAutoSync() {
        if (!autoSyncEnabled) return
        autoSyncJob?.cancel()
        val intervalMs = settings.autoSyncIntervalMinutes.coerceAtLeast(1) * 60_000L
        autoSyncJob = scope.launch {
            while (isActive) {
                delay(intervalMs)
                quietSync() // periodic poll to pull other devices' changes
            }
        }
    }

    private fun stopAutoSync() {
        autoSyncJob?.cancel(); autoSyncJob = null
        pendingSyncJob?.cancel(); pendingSyncJob = null
    }

    /** Debounced sync shortly after a local change, so edits propagate without waiting for the poll. */
    private fun scheduleAutoSyncSoon() {
        if (!autoSyncEnabled) return
        pendingSyncJob?.cancel()
        pendingSyncJob = scope.launch {
            delay(4_000)
            quietSync()
        }
    }

    /** Background sync: no modal overlay, no modal errors; yields to any manual operation. */
    private suspend fun quietSync() {
        if (busy || autoSyncing) return
        val v = vault ?: return
        if (targets.none { it.enabled }) return
        val e = ensureEngine() ?: return
        autoSyncing = true
        try {
            withVault { reconcileLinkedFoldersSync(it) }
            performSync(e, v)
        } catch (_: Exception) {
            // surfaced via per-target status, not as a modal error
        } finally {
            autoSyncing = false
        }
    }

    // --- Linked folders (kept mirrored into the vault) ---

    fun linkFolder(folder: Path) = run("Linking ${folder.fileName}…") {
        val prefix = (folder.fileName?.toString() ?: "linked").trim('/').ifBlank { "linked" }
        withVault { it.syncFromSource(folder, prefix) }
        linkedFolders = linkedFolders + LinkedFolder(folder.toString(), prefix)
        linkedFolderStore.save(linkedFolders)
        refresh()
        restartWatcher()
        scheduleAutoSyncSoon()
        statusMessage = "Linked ${folder.fileName} — kept mirrored as it changes"
    }

    fun unlinkFolder(folder: LinkedFolder) {
        linkedFolders = linkedFolders - folder
        linkedFolderStore.save(linkedFolders)
        restartWatcher()
        statusMessage = "Unlinked ${folder.prefix} (existing encrypted copies kept)"
    }

    fun rescanLinkedFolders() = run("Rescanning linked folders…") {
        val changed = withVault { reconcileLinkedFoldersSync(it) } ?: 0
        refresh()
        statusMessage = if (changed > 0) "Rescanned — $changed change(s)" else "Linked folders already up to date"
        if (changed > 0) scheduleAutoSyncSoon()
    }

    /** Synchronous reconcile; callers must hold the vault lock (via [withVault] or [performSync]). */
    private fun reconcileLinkedFoldersSync(v: Vault): Int {
        if (linkedFolders.isEmpty()) return 0
        return linkedFolders.sumOf { lf ->
            val src = Paths.get(lf.source)
            if (java.nio.file.Files.isDirectory(src)) v.syncFromSource(src, lf.prefix).changed else 0
        }
    }

    private fun restartWatcher() {
        folderWatcher?.close()
        folderWatcher = null
        if (vault == null || linkedFolders.isEmpty()) return
        val roots = linkedFolders.map { Paths.get(it.source) }.filter { java.nio.file.Files.isDirectory(it) }
        folderWatcher = FolderWatcher { onWatchedFolderChange() }.also { it.watch(roots) }
    }

    /** Called off-thread by the watcher; marshals back onto the VM scope for a quiet reconcile+sync. */
    private fun onWatchedFolderChange() {
        scope.launch {
            if (busy || autoSyncing) return@launch
            val changed = withVault { reconcileLinkedFoldersSync(it) } ?: 0
            if (changed > 0) {
                refresh()
                scheduleAutoSyncSoon()
            }
        }
    }

    private suspend fun ensureEngine(): StorageEngine? = engineMutex.withLock {
        engine?.let { return@withLock it }
        try {
            val started = withContext(Dispatchers.IO) {
                RcloneDaemon.start(configPath = homeDir.resolve("rclone.conf"))
            }
            daemon = started
            rcloneAvailable = true
            RcloneStorageEngine(started.rc).also { engine = it }
        } catch (e: Exception) {
            rcloneAvailable = false
            error = "rclone unavailable: ${e.message}"
            null
        }
    }

    fun createVault(password: CharArray) = run("Creating encrypted vault…") {
        val created = withContext(Dispatchers.Default) { VaultFactory.create(vaultRoot, password, deviceId = deviceId) }
        vault = created.vault
        recoveryKey = created.recoveryKey
        refresh()
        startAutoSync()
        restartWatcher()
        screen = Screen.RecoveryReveal
    }

    fun acknowledgeRecoveryKey() {
        recoveryKey = null
        screen = Screen.Browser
    }

    fun unlock(password: CharArray) = run("Unlocking…") {
        vault = withContext(Dispatchers.Default) { VaultFactory.unlockWithPassword(vaultRoot, password, deviceId) }
        refresh()
        screen = Screen.Browser
        startAutoSync()
        restartWatcher()
    }

    fun unlockWithRecovery(key: String) = run("Unlocking with recovery key…") {
        vault = withContext(Dispatchers.Default) { VaultFactory.unlockWithRecovery(vaultRoot, key, deviceId) }
        refresh()
        screen = Screen.Browser
        startAutoSync()
        restartWatcher()
    }

    fun lock() {
        stopAutoSync()
        folderWatcher?.close(); folderWatcher = null
        vault?.close()
        vault = null
        files = emptyList()
        screen = Screen.Unlock
    }

    fun addFiles(paths: List<Path>) = run("Encrypting ${paths.size} item(s)…") {
        withVault { v ->
            paths.forEach { p ->
                if (java.nio.file.Files.isDirectory(p)) v.addFolder(p) else v.addFile(p, p.fileName.toString())
            }
        }
        refresh()
        statusMessage = "Added ${paths.size} item(s)"
        scheduleAutoSyncSoon()
    }

    fun exportFile(entry: FileEntry, dest: Path) = run("Decrypting…") {
        withVault { it.exportFile(entry.path, dest) }
        statusMessage = "Exported ${entry.path}"
    }

    fun deleteFile(entry: FileEntry) = run("Removing…") {
        withVault { it.removeFile(entry.path) }
        refresh()
        statusMessage = "Removed ${entry.path}"
        scheduleAutoSyncSoon()
    }

    fun resolveConflict(entry: FileEntry, keepConflictVersion: Boolean) = run("Resolving conflict…") {
        withVault { it.resolveConflict(entry.path, keepConflictVersion) }
        refresh()
        statusMessage = if (keepConflictVersion) "Kept ${entry.path}" else "Discarded ${entry.path}"
        scheduleAutoSyncSoon()
    }

    fun changeMasterPassword(newPassword: CharArray) = run("Changing master password…") {
        withVault { it.changePassword(newPassword) }
        statusMessage = "Master password changed"
    }

    fun regenerateRecoveryKey() = run("Generating a new recovery key…") {
        val key = withVault { it.regenerateRecoveryKey() } ?: return@run
        recoveryKey = key
        screen = Screen.RecoveryReveal
    }

    private fun refresh() { files = vault?.list() ?: emptyList() }

    private fun run(message: String, block: suspend () -> Unit) {
        scope.launch {
            busy = true
            busyMessage = message
            error = null
            statusMessage = null
            try {
                block()
            } catch (e: WrongCredentialsException) {
                error = "Incorrect password or recovery key."
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            } finally {
                busy = false
                busyMessage = ""
            }
        }
    }
}
