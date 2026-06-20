package dev.vaultmesh.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.vaultmesh.crypto.WrongCredentialsException
import dev.vaultmesh.storage.RemoteEntry
import dev.vaultmesh.storage.StorageEngine
import dev.vaultmesh.storage.StorageTarget
import dev.vaultmesh.storage.StorageUsage
import dev.vaultmesh.storage.TargetKind
import dev.vaultmesh.storage.rootFs
import dev.vaultmesh.storage.rclone.RcloneBinary
import dev.vaultmesh.storage.rclone.RcloneDaemon
import dev.vaultmesh.storage.rclone.RcloneStorageEngine
import dev.vaultmesh.sync.TargetStore
import dev.vaultmesh.sync.VaultReplicator
import dev.vaultmesh.vault.DeviceId
import dev.vaultmesh.vault.FileEntry
import dev.vaultmesh.vault.Vault
import dev.vaultmesh.vault.VaultFactory
import dev.vaultmesh.vault.VaultTree
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.nio.file.Paths
import java.util.UUID

enum class Screen { Onboarding, Restore, Unlock, RecoveryReveal, Files, Settings, Help }

/** A file the user double-clicked; the UI asks how to open it (read-only vs editable). */
data class PendingOpen(val node: VaultTree.Node)

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
    var statusMessage by mutableStateOf<String?>(null)
        private set

    // --- File-manager navigation ---
    /** The folder currently shown ("" is the vault root). */
    var currentDir by mutableStateOf("")
        private set
    /** Live entries (all folders/files, non-deleted) — the source for tree + info. */
    var entries by mutableStateOf<List<FileEntry>>(emptyList())
        private set
    /** Immediate children (folders first, then files) of [currentDir]. */
    var children by mutableStateOf<List<VaultTree.Node>>(emptyList())
        private set
    var selectedPath by mutableStateOf<String?>(null)
        private set
    var pendingOpen by mutableStateOf<PendingOpen?>(null)
        private set

    // --- Replication / sync (Phase 2 & 3) ---
    private val homeDir: Path = Paths.get(System.getProperty("user.home"), ".vaultmesh")
    private val targetStore = TargetStore(homeDir.resolve("config").resolve("targets.json"))
    private val settingsStore = AppSettingsStore(homeDir.resolve("config").resolve("settings.json"))
    private val linkedFolderStore = LinkedFolderStore(homeDir.resolve("config").resolve("linked-folders.tsv"))
    private val syncStatusStore = SyncStatusStore(homeDir.resolve("config").resolve("sync-state"))
    private val openFiles = OpenFilesManager(homeDir.resolve("open"))
    private val keychain = KeychainStore()
    private val deviceId: String = DeviceId.loadOrCreate(homeDir.resolve("config").resolve("device-id"))
    private var daemon: RcloneDaemon? = null
    private var engine: StorageEngine? = null

    private var settings = settingsStore.load()
    private var autoSyncJob: Job? = null
    private var pendingSyncJob: Job? = null
    private var openWatchJob: Job? = null
    private var gcJob: Job? = null
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
    /** Opt-in "stay unlocked" (macOS Keychain) — skip the master password on launch. */
    var stayUnlockedEnabled by mutableStateOf(settings.stayUnlocked)
        private set
    val keychainSupported: Boolean = keychain.isSupported()
    var autoSyncing by mutableStateOf(false)
        private set
    var lastSyncLabel by mutableStateOf<String?>(null)
        private set
    var linkedFolders by mutableStateOf(linkedFolderStore.load())
        private set

    /** Per-target capacity/usage (targetId → usage; absent = not loaded, null = backend doesn't report). */
    var storageUsage by mutableStateOf<Map<String, StorageUsage?>>(emptyMap())
        private set
    var storageUsageLoading by mutableStateOf(false)
        private set

    // --- Cloud storage explorer (a dedicated window) ---
    var explorerTarget by mutableStateOf<StorageTarget?>(null)
        private set
    /** Current sub-path within the explored storage root ("" = the root). */
    var explorerPath by mutableStateOf("")
        private set
    var explorerEntries by mutableStateOf<List<RemoteEntry>>(emptyList())
        private set
    var explorerLoading by mutableStateOf(false)
        private set
    var explorerError by mutableStateOf<String?>(null)
        private set
    var explorerUsage by mutableStateOf<StorageUsage?>(null)
        private set
    var explorerUsageLoaded by mutableStateOf(false)
        private set

    // --- Storage maintenance (garbage collection) ---
    /** Bytes of orphaned encrypted objects collectGarbage() could reclaim (refreshed on demand). */
    var reclaimableBytes by mutableStateOf(0L)
        private set
    var reclaimableObjects by mutableStateOf(0)
        private set

    // --- Per-item sync status ---
    private var pendingPaths: Set<String> = emptySet()
    private var everSynced: Boolean = false
    var syncingNow by mutableStateOf(false)
        private set
    var lastSyncError by mutableStateOf(false)
        private set

    init {
        val s = syncStatusStore.load()
        pendingPaths = s.pending
        everSynced = s.everSynced
        Runtime.getRuntime().addShutdownHook(
            Thread { folderWatcher?.close(); openFiles.wipeAll(); daemon?.close() },
        )
        tryAutoUnlock()
    }

    val vaultPath: String get() = vaultRoot.toString()

    fun dismissError() { error = null }

    fun openSettings() { screen = Screen.Settings; refreshRemotes(); refreshStorageUsage(); refreshReclaimable() }
    fun closeSettings() { screen = Screen.Files }

    private var screenBeforeHelp = Screen.Unlock
    fun openHelp() { screenBeforeHelp = screen; screen = Screen.Help }
    fun closeHelp() { screen = screenBeforeHelp }

    // --- Navigation -----------------------------------------------------------

    fun navigateInto(node: VaultTree.Node) {
        if (!node.isDir) return
        currentDir = node.path
        selectedPath = null
        refresh()
    }

    fun navigateTo(dir: String) {
        currentDir = dir.trim('/')
        selectedPath = null
        refresh()
    }

    fun navigateUp() = navigateTo(currentDir.substringBeforeLast('/', ""))

    /** Breadcrumb segments as (label, fullPath) pairs, starting with the root. */
    fun breadcrumbs(): List<Pair<String, String>> {
        val crumbs = mutableListOf("My Files" to "")
        if (currentDir.isNotEmpty()) {
            var acc = ""
            currentDir.split('/').forEach { seg ->
                acc = if (acc.isEmpty()) seg else "$acc/$seg"
                crumbs += seg to acc
            }
        }
        return crumbs
    }

    fun select(path: String?) { selectedPath = path }

    fun selectedEntry(): FileEntry? = selectedPath?.let { sp -> entries.firstOrNull { it.path == sp } }

    // --- Storage targets / providers (unchanged behavior) ---------------------

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

    // --- Storage capacity / quota ---------------------------------------------

    /** Loads each enabled target's quota (parallel, off-thread). Cheap to call again to refresh. */
    fun refreshStorageUsage() {
        if (storageUsageLoading) return
        scope.launch {
            val e = ensureEngine() ?: return@launch
            val enabled = targets.filter { it.enabled }
            if (enabled.isEmpty()) { storageUsage = emptyMap(); return@launch }
            storageUsageLoading = true
            try {
                val results = enabled.map { t ->
                    async(Dispatchers.IO) { t.id to runCatching { e.usage(t.rootFs()) }.getOrNull() }
                }.awaitAll()
                storageUsage = storageUsage + results.toMap()
            } finally {
                storageUsageLoading = false
            }
        }
    }

    // --- Cloud storage explorer -----------------------------------------------

    /** Opens the dedicated explorer window on [target], rooted at its backend (the whole remote). */
    fun openExplorer(target: StorageTarget) {
        explorerTarget = target
        explorerPath = ""
        explorerError = null
        explorerEntries = emptyList()
        explorerUsage = null
        explorerUsageLoaded = false
        loadExplorer()
        scope.launch {
            val e = ensureEngine() ?: return@launch
            explorerUsage = runCatching { e.usage(target.rootFs()) }.getOrNull()
            explorerUsageLoaded = true
        }
    }

    fun closeExplorer() {
        explorerTarget = null
        explorerEntries = emptyList()
        explorerPath = ""
        explorerError = null
        explorerUsage = null
        explorerUsageLoaded = false
    }

    fun explorerNavigate(subPath: String) {
        explorerPath = subPath.trim('/')
        loadExplorer()
    }

    fun explorerOpen(entry: RemoteEntry) { if (entry.isDir) explorerNavigate(entry.path) }

    fun explorerUp() = explorerNavigate(explorerPath.substringBeforeLast('/', ""))

    fun refreshExplorer() = loadExplorer()

    /** Breadcrumb segments (label, fullSubPath) for the explorer, starting at the storage root. */
    fun explorerBreadcrumbs(): List<Pair<String, String>> {
        val root = explorerTarget?.displayName ?: "Storage"
        val crumbs = mutableListOf(root to "")
        if (explorerPath.isNotEmpty()) {
            var acc = ""
            explorerPath.split('/').forEach { seg ->
                acc = if (acc.isEmpty()) seg else "$acc/$seg"
                crumbs += seg to acc
            }
        }
        return crumbs
    }

    private fun loadExplorer() {
        val target = explorerTarget ?: return
        explorerLoading = true
        explorerError = null
        scope.launch {
            val e = ensureEngine()
            if (e == null) { explorerLoading = false; explorerError = "Storage engine unavailable."; return@launch }
            val res = runCatching { e.list(target.rootFs(), explorerPath) }
            explorerLoading = false
            res.onSuccess { list ->
                explorerEntries = list.sortedWith(
                    compareByDescending<RemoteEntry> { it.isDir }.thenBy { it.name.lowercase() },
                )
            }.onFailure {
                explorerEntries = emptyList()
                explorerError = it.message ?: "Couldn't list this folder."
            }
        }
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

    /**
     * Two-way sync against every enabled target. Non-modal: it never shows the blocking overlay, so
     * you can keep browsing and editing while it runs (file edits and the sync interleave because the
     * vault lock is held only for the brief in-memory merge, never during network transfers).
     */
    fun syncNow() {
        if (syncingNow) return
        scope.launch {
            error = null
            val e = ensureEngine() ?: return@launch
            val v = vault ?: return@launch
            try {
                withVault { reconcileLinkedFoldersSync(it) } // pick up on-disk changes first
                statusMessage = performSync(e, v)
            } catch (ex: Exception) {
                error = ex.message ?: "Sync failed"
                lastSyncError = true
                syncingNow = false
            }
        }
    }

    /**
     * Shared sync routine for manual + background use. Two phases, both run targets in parallel:
     *   1. pull each peer + merge it locally (only the merge takes the vault lock — no network in it);
     *   2. push the merged vault to every target.
     * Doing all merges before any push means a single round propagates every device's changes, and
     * rclone only transfers the files that actually changed.
     */
    private suspend fun performSync(e: StorageEngine, v: Vault): String {
        val enabled = targets.filter { it.enabled }
        if (enabled.isEmpty()) return "No storage targets enabled."
        if (syncingNow) return "Sync already in progress." // runs on the Main dispatcher, so this guards reentry
        syncingNow = true
        targetStatus = enabled.associate { it.id to "Syncing…" }
        var conflicts = 0
        val status = LinkedHashMap<String, String>()
        try {
            // Phase 1 — pull + merge (network in parallel; the merge is serialized by the vault lock).
            val pulled = enabled.map { t ->
                scope.async(Dispatchers.IO) {
                    t to runCatching {
                        val staging = homeDir.resolve("staging").resolve(t.id)
                        java.nio.file.Files.createDirectories(staging)
                        val pull = e.pull(t, staging)
                        val merge = vaultMutex.withLock { withContext(Dispatchers.Default) { v.mergeFrom(staging) } }
                        pull to merge
                    }
                }
            }.awaitAll()
            pulled.forEach { (t, r) ->
                r.onSuccess { (_, merge) -> conflicts += merge.conflicts.size }
                    .onFailure { status[t.id] = "Error: ${it.message ?: "pull failed"}" }
            }
            // Phase 2 — push the merged vault to every target (parallel).
            val pushed = enabled.map { t ->
                scope.async(Dispatchers.IO) { t to runCatching { e.push(v.root, t) } }
            }.awaitAll()
            pushed.forEach { (t, r) ->
                r.onSuccess { push -> status[t.id] = if (push.ok) "Synced" else "Error: ${push.error ?: "push failed"}" }
                    .onFailure { status[t.id] = "Error: ${it.message ?: "push failed"}" }
            }
        } finally {
            syncingNow = false
        }
        val okCount = enabled.count { status[it.id] == "Synced" }
        targetStatus = status
        // The vault is "fully synced" only when every enabled target accepted the push.
        if (okCount == enabled.size) markAllSynced() else lastSyncError = true
        refresh() // sync may have pulled new files or created conflict copies
        refreshStorageUsage() // capacity shifts after a push
        scheduleGc() // merges can supersede local files, orphaning their old chunks
        lastSyncLabel = "Last synced just now"
        return buildString {
            append("Synced $okCount/${enabled.size} target(s)")
            if (conflicts > 0) append(" — $conflicts conflict copies created")
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
        if (busy || autoSyncing || syncingNow) return
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

    // --- Sync status bookkeeping ---

    private fun markDirty(paths: Collection<String>) {
        val add = paths.filter { it.isNotBlank() }
        if (add.isEmpty()) return
        pendingPaths = pendingPaths + add
        lastSyncError = false
        persistSyncStatus()
    }

    private fun markAllSynced() {
        pendingPaths = emptySet()
        everSynced = true
        lastSyncError = false
        persistSyncStatus()
    }

    private fun persistSyncStatus() =
        syncStatusStore.save(SyncStatusStore.State(everSynced, pendingPaths))

    private fun liveFilePaths(): Set<String> =
        vault?.list()?.filter { !it.isDir && !it.deleted }?.map { it.path }?.toSet() ?: emptySet()

    /** The sync badge for a single file path. */
    private fun stateOf(path: String): FileSyncState = when {
        targets.none { it.enabled } -> FileSyncState.LocalOnly
        path in pendingPaths -> when {
            syncingNow -> FileSyncState.Syncing
            lastSyncError -> FileSyncState.Error
            else -> FileSyncState.Pending
        }
        everSynced -> FileSyncState.Synced
        syncingNow -> FileSyncState.Syncing
        else -> FileSyncState.Pending
    }

    /** The sync badge for any tree node (folders aggregate their descendant files). */
    fun nodeSyncState(node: VaultTree.Node): FileSyncState {
        if (targets.none { it.enabled }) return FileSyncState.LocalOnly
        if (!node.isDir) return stateOf(node.path)
        val files = VaultTree.descendantFiles(entries, node.path)
        if (files.isEmpty()) return if (everSynced) FileSyncState.Synced else FileSyncState.Pending
        val states = files.map { stateOf(it.path) }
        return when {
            states.any { it == FileSyncState.Error } -> FileSyncState.Error
            states.any { it == FileSyncState.Syncing } -> FileSyncState.Syncing
            states.any { it == FileSyncState.Pending } -> FileSyncState.Pending
            else -> FileSyncState.Synced
        }
    }

    /** Overall vault sync state for the toolbar chip. */
    fun overallSyncState(): FileSyncState = when {
        targets.none { it.enabled } -> FileSyncState.LocalOnly
        syncingNow || autoSyncing -> FileSyncState.Syncing
        lastSyncError -> FileSyncState.Error
        pendingPaths.isNotEmpty() -> FileSyncState.Pending
        everSynced -> FileSyncState.Synced
        else -> FileSyncState.Pending
    }

    // --- Storage maintenance (garbage collection) -----------------------------

    /** Manual "reclaim space": sweep orphaned objects now and report what was freed. */
    fun reclaimSpace() = run("Reclaiming space…") {
        val res = withVault { it.collectGarbage() } ?: return@run
        reclaimableObjects = 0
        reclaimableBytes = 0
        refreshStorageUsage() // local mirror's free space changed
        statusMessage = if (res.didReclaim) {
            "Reclaimed ${humanBytes(res.bytesFreed)} from ${res.removedObjects} orphaned object(s)."
        } else {
            "Nothing to reclaim — your local vault is already compact."
        }
    }

    /** Recomputes how much GC could reclaim (dry run), for the Settings readout. */
    fun refreshReclaimable() {
        scope.launch {
            val res = withVault { it.garbageStats() } ?: return@launch
            reclaimableObjects = res.removedObjects
            reclaimableBytes = res.bytesFreed
        }
    }

    /** Debounced, quiet GC after operations that orphan objects (deletes, edits, syncs). */
    private fun scheduleGc() {
        gcJob?.cancel()
        gcJob = scope.launch {
            delay(8_000)
            if (busy || syncingNow || autoSyncing) { scheduleGc(); return@launch } // defer until idle
            val res = withVault { it.collectGarbage() } ?: return@launch
            if (res.didReclaim) refreshStorageUsage()
            reclaimableObjects = 0
            reclaimableBytes = 0
        }
    }

    private fun humanBytes(n: Long): String = when {
        n < 1024 -> "$n B"
        n < 1024 * 1024 -> "%.1f KB".format(n / 1024.0)
        n < 1024L * 1024 * 1024 -> "%.1f MB".format(n / (1024.0 * 1024))
        else -> "%.2f GB".format(n / (1024.0 * 1024 * 1024))
    }

    // --- Linked folders (kept mirrored into the vault) ---

    fun linkFolder(folder: Path) = run("Linking ${folder.fileName}…") {
        val prefix = (folder.fileName?.toString() ?: "linked").trim('/').ifBlank { "linked" }
        val before = liveFilePaths()
        withVault { it.syncFromSource(folder, prefix) }
        markDirty(liveFilePaths() - before)
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
        val before = liveFilePaths()
        val changed = withVault { reconcileLinkedFoldersSync(it) } ?: 0
        markDirty(liveFilePaths() - before)
        refresh()
        statusMessage = if (changed > 0) "Rescanned — $changed change(s)" else "Linked folders already up to date"
        if (changed > 0) { scheduleAutoSyncSoon(); scheduleGc() }
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
            val before = liveFilePaths()
            val changed = withVault { reconcileLinkedFoldersSync(it) } ?: 0
            if (changed > 0) {
                markDirty(liveFilePaths() - before)
                refresh()
                scheduleAutoSyncSoon()
                scheduleGc()
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

    // --- Lifecycle ------------------------------------------------------------

    fun createVault(password: CharArray) = run("Creating encrypted vault…") {
        val created = withContext(Dispatchers.Default) { VaultFactory.create(vaultRoot, password, deviceId = deviceId) }
        vault = created.vault
        recoveryKey = created.recoveryKey
        currentDir = ""
        refresh()
        startAutoSync()
        restartWatcher()
        startOpenWatch()
        screen = Screen.RecoveryReveal
    }

    fun acknowledgeRecoveryKey() {
        recoveryKey = null
        screen = Screen.Files
    }

    // --- Restore an existing vault on a new device --------------------------

    fun beginRestore() { screen = Screen.Restore; refreshRemotes() }
    fun cancelRestore() { screen = Screen.Onboarding }

    /** Restore from a local/external folder that holds a VaultMesh mirror. */
    fun restoreFromLocalFolder(folder: Path) = run("Downloading your vault…") {
        // Accept either the parent folder (…/VaultMesh) or the vault directory itself.
        val withSub = folder.resolve("VaultMesh")
        val fsRoot = if (java.nio.file.Files.exists(withSub.resolve("vault.json"))) withSub else folder
        doRestore(StorageTarget(UUID.randomUUID().toString(), folder.fileName?.toString() ?: "Local", TargetKind.LOCAL_FOLDER, fsRoot.toString()))
    }

    /** Restore from an rclone remote that's already configured. */
    fun restoreFromRemote(remoteName: String) = run("Downloading your vault…") {
        doRestore(StorageTarget(UUID.randomUUID().toString(), remoteName, TargetKind.RCLONE_REMOTE, "$remoteName:VaultMesh"))
    }

    /** Connect a new provider (OAuth/credentials) and immediately restore the vault it holds. */
    fun connectAndRestore(provider: Provider, remoteName: String, fieldValues: Map<String, String>) =
        run(if (provider.oauth) "Opening browser to sign in to ${provider.displayName}…" else "Connecting ${provider.displayName}…") {
            val e = ensureEngine() ?: return@run
            val params = provider.fixedParams + fieldValues.filterValues { it.isNotBlank() }
            withContext(Dispatchers.IO) { e.createRemote(remoteName, provider.type, params) }
            configuredRemotes = runCatching { e.listConfiguredRemotes() }.getOrDefault(configuredRemotes)
            doRestore(StorageTarget(UUID.randomUUID().toString(), remoteName, TargetKind.RCLONE_REMOTE, "$remoteName:VaultMesh"))
        }

    /** Pulls an existing vault from [target] into the local root, then routes to Unlock. */
    private suspend fun doRestore(target: StorageTarget) {
        if (VaultFactory.exists(vaultRoot)) { error = "A vault already exists on this device."; return }
        val e = ensureEngine() ?: return
        java.nio.file.Files.createDirectories(vaultRoot)
        withContext(Dispatchers.IO) { e.pull(target, vaultRoot) }
        if (!VaultFactory.exists(vaultRoot)) {
            error = "No VaultMesh vault found there. Make sure this is the storage your other device syncs to."
            return
        }
        updateTargets(targets + target.copy(enabled = true)) // keep syncing to it after unlock
        everSynced = true
        pendingPaths = emptySet()
        persistSyncStatus()
        screen = Screen.Unlock
        statusMessage = "Vault downloaded. Unlock with your master password to open your files."
    }

    fun unlock(password: CharArray) = run("Unlocking…") {
        vault = withContext(Dispatchers.Default) { VaultFactory.unlockWithPassword(vaultRoot, password, deviceId) }
        onVaultOpened()
    }

    fun unlockWithRecovery(key: String) = run("Unlocking with recovery key…") {
        vault = withContext(Dispatchers.Default) { VaultFactory.unlockWithRecovery(vaultRoot, key, deviceId) }
        onVaultOpened()
    }

    /** Shared post-unlock wiring used by every unlock path (password, recovery, key material). */
    private suspend fun onVaultOpened() {
        currentDir = ""
        refresh()
        screen = Screen.Files
        startAutoSync()
        restartWatcher()
        startOpenWatch()
        refreshStorageUsage()
        if (stayUnlockedEnabled && keychainSupported) persistKeyMaterial()
    }

    /** Auto-unlock at launch from the keychain, if the user opted in. Falls back to manual on any error. */
    private fun tryAutoUnlock() {
        if (!VaultFactory.exists(vaultRoot) || !stayUnlockedEnabled || !keychainSupported) return
        scope.launch {
            busy = true; busyMessage = "Opening your vault…"
            try {
                val hex = withContext(Dispatchers.IO) { keychain.load() } ?: return@launch
                val vmk = runCatching { hex.hexToBytes() }.getOrNull() ?: return@launch
                try {
                    vault = withContext(Dispatchers.Default) { VaultFactory.unlockWithKeyMaterial(vaultRoot, vmk, deviceId) }
                    onVaultOpened()
                } catch (_: Exception) {
                    keychain.clear() // stale/invalid — require the password again
                } finally {
                    vmk.fill(0)
                }
            } finally {
                busy = false; busyMessage = ""
            }
        }
    }

    /** Stashes the current vault's key material in the keychain (best effort). */
    private suspend fun persistKeyMaterial() {
        val vmk = withVault { it.exportKeyMaterial() } ?: return
        try {
            withContext(Dispatchers.IO) { keychain.store(vmk.toHex()) }
        } finally {
            vmk.fill(0)
        }
    }

    /** Toggles opt-in stay-unlocked: stores the key on enable, removes it on disable. */
    fun setStayUnlocked(enabled: Boolean) {
        if (enabled && !keychainSupported) {
            error = "Stay unlocked needs the macOS Keychain and isn't available on this system."
            return
        }
        stayUnlockedEnabled = enabled
        settings = settings.copy(stayUnlocked = enabled)
        settingsStore.save(settings)
        scope.launch {
            if (enabled) {
                persistKeyMaterial()
                statusMessage = "This device will open without the master password. Use Lock to require it again."
            } else {
                withContext(Dispatchers.IO) { keychain.clear() }
                statusMessage = "Stay unlocked turned off — the master password is required on next launch."
            }
        }
    }

    fun lock() {
        stopAutoSync()
        openWatchJob?.cancel(); openWatchJob = null
        gcJob?.cancel(); gcJob = null
        openFiles.wipeAll()
        folderWatcher?.close(); folderWatcher = null
        closeExplorer() // don't keep a storage window showing decrypted file names after locking
        vault?.close()
        vault = null
        entries = emptyList()
        children = emptyList()
        currentDir = ""
        selectedPath = null
        screen = Screen.Unlock
    }

    // --- File operations ------------------------------------------------------

    /** Imports files/folders from disk into the current folder. */
    fun addPaths(paths: List<Path>) = run("Encrypting ${paths.size} item(s)…") {
        val before = liveFilePaths()
        withVault { v ->
            paths.forEach { p ->
                val name = p.fileName?.toString() ?: return@forEach
                val target = childPath(name)
                if (java.nio.file.Files.isDirectory(p)) v.addFolder(p, target) else v.addFile(p, target)
            }
        }
        markDirty(liveFilePaths() - before)
        refresh()
        statusMessage = "Added ${paths.size} item(s)"
        scheduleAutoSyncSoon()
    }

    fun createFolder(name: String) = run("Creating folder…") {
        val clean = name.trim().trim('/')
        if (clean.isEmpty()) return@run
        withVault { it.createFolder(childPath(clean)) }
        refresh()
        statusMessage = "Created folder \"$clean\""
    }

    fun rename(node: VaultTree.Node, newName: String) = run("Renaming…") {
        val clean = newName.trim().trim('/')
        if (clean.isEmpty() || clean == node.name) return@run
        val parent = node.path.substringBeforeLast('/', "")
        val dest = if (parent.isEmpty()) clean else "$parent/$clean"
        val before = liveFilePaths()
        withVault { it.move(node.path, dest) }
        markDirty(liveFilePaths() - before)
        refresh()
        statusMessage = "Renamed to \"$clean\""
        scheduleAutoSyncSoon()
    }

    /** Moves [node] into [destDir] (a folder path, "" for root). */
    fun moveInto(node: VaultTree.Node, destDir: String) = run("Moving…") {
        val dir = destDir.trim('/')
        if (dir == node.path || dir.startsWith("${node.path}/")) {
            error = "Can't move a folder into itself."
            return@run
        }
        val dest = if (dir.isEmpty()) node.name else "$dir/${node.name}"
        if (dest == node.path) return@run
        val before = liveFilePaths()
        withVault { it.move(node.path, dest) }
        markDirty(liveFilePaths() - before)
        refresh()
        statusMessage = "Moved \"${node.name}\""
        scheduleAutoSyncSoon()
    }

    fun delete(node: VaultTree.Node) = run("Removing…") {
        withVault { v -> if (node.isDir) v.removeFolder(node.path) else v.removeFile(node.path) }
        if (selectedPath == node.path) selectedPath = null
        refresh()
        statusMessage = "Removed \"${node.name}\""
        scheduleAutoSyncSoon()
        scheduleGc() // the removed file's encrypted objects are now orphaned
    }

    fun exportNode(node: VaultTree.Node, dest: Path) = run("Decrypting…") {
        withVault { it.exportFile(node.path, dest) }
        statusMessage = "Exported \"${node.name}\""
    }

    /** Step 1 of opening: ask the user read-only vs editable. */
    fun requestOpen(node: VaultTree.Node) {
        if (node.isDir) { navigateInto(node); return }
        if (!openFiles.canOpen()) {
            error = "Opening files in another app isn't supported on this system. Use Export instead."
            return
        }
        pendingOpen = PendingOpen(node)
    }

    fun cancelOpen() { pendingOpen = null }

    /** Step 2: actually decrypt to a temp file and hand it to the OS-associated app. */
    fun confirmOpen(editable: Boolean) {
        val node = pendingOpen?.node ?: return
        pendingOpen = null
        run("Opening ${node.name}…") {
            val v = vault ?: return@run
            withContext(Dispatchers.IO) {
                vaultMutex.withLock {
                    openFiles.open(node.path, node.name, editable) { temp -> v.exportFile(node.path, temp) }
                }
            }
            statusMessage = if (editable) {
                "Opened \"${node.name}\" — your edits are saved back into the vault automatically."
            } else {
                "Opened \"${node.name}\" read-only."
            }
        }
    }

    /** Polls open editable temp files; re-encrypts external edits back into the vault. */
    private fun startOpenWatch() {
        openWatchJob?.cancel()
        openWatchJob = scope.launch {
            while (isActive) {
                delay(1_500)
                if (vault == null) continue
                val changed = withContext(Dispatchers.IO) { openFiles.pollChanged() }
                for (f in changed) {
                    if (!java.nio.file.Files.isReadable(f.tempFile)) continue
                    withVault { it.addFile(f.tempFile, f.vaultPath) }
                    openFiles.refreshStamp(f.vaultPath)
                    markDirty(listOf(f.vaultPath))
                    refresh()
                    statusMessage = "Saved changes to \"${f.vaultPath.substringAfterLast('/')}\""
                    scheduleAutoSyncSoon()
                    scheduleGc() // the previous version's chunks are now orphaned
                }
            }
        }
    }

    // --- Security -------------------------------------------------------------

    fun changeMasterPassword(newPassword: CharArray) = run("Changing master password…") {
        withVault { it.changePassword(newPassword) }
        statusMessage = "Master password changed"
    }

    fun regenerateRecoveryKey() = run("Generating a new recovery key…") {
        val key = withVault { it.regenerateRecoveryKey() } ?: return@run
        recoveryKey = key
        screen = Screen.RecoveryReveal
    }

    fun resolveConflict(entry: FileEntry, keepConflictVersion: Boolean) = run("Resolving conflict…") {
        val before = liveFilePaths()
        withVault { it.resolveConflict(entry.path, keepConflictVersion) }
        markDirty(liveFilePaths() - before)
        refresh()
        statusMessage = if (keepConflictVersion) "Kept \"${entry.path}\"" else "Discarded conflict copy"
        scheduleAutoSyncSoon()
        scheduleGc() // discarding/replacing a conflict copy orphans its chunks
    }

    // --- Internals ------------------------------------------------------------

    /** Full vault path of [name] inside the current folder. */
    private fun childPath(name: String): String =
        if (currentDir.isEmpty()) name else "$currentDir/$name"

    private fun refresh() {
        entries = vault?.list() ?: emptyList()
        ensureValidDir()
        children = VaultTree.children(entries, currentDir)
        selectedPath?.let { sp -> if (entries.none { it.path == sp }) selectedPath = null }
    }

    /** If the current folder vanished (e.g. it was deleted), walk up to the nearest existing one. */
    private fun ensureValidDir() {
        var d = currentDir
        while (d.isNotEmpty() && entries.none { it.path == d || it.path.startsWith("$d/") }) {
            d = d.substringBeforeLast('/', "")
        }
        if (d != currentDir) currentDir = d
    }

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

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

private fun String.hexToBytes(): ByteArray {
    require(length % 2 == 0) { "odd-length hex" }
    return ByteArray(length / 2) { ((this[it * 2].digitToInt(16) shl 4) or this[it * 2 + 1].digitToInt(16)).toByte() }
}
