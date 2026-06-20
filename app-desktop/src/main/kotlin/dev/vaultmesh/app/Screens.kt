@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package dev.vaultmesh.app

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.vaultmesh.storage.RemoteEntry
import dev.vaultmesh.storage.StorageTarget
import dev.vaultmesh.storage.StorageUsage
import dev.vaultmesh.storage.TargetKind
import dev.vaultmesh.vault.FileEntry
import dev.vaultmesh.vault.VaultTree

internal val isMac = System.getProperty("os.name").lowercase().contains("mac")

/**
 * On macOS the window uses full-window content with a transparent title bar (see Main.kt), so the
 * traffic-light buttons overlay the top of our content. This inset keeps headers clear of them.
 */
internal val titleBarInset = if (isMac) 26.dp else 0.dp

@Composable
fun AppRoot(vm: AppViewModel) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            when (vm.screen) {
                Screen.Onboarding -> OnboardingScreen(vm)
                Screen.Restore -> RestoreScreen(vm)
                Screen.Unlock -> UnlockScreen(vm)
                Screen.RecoveryReveal -> RecoveryRevealScreen(vm)
                Screen.Files -> FilesScreen(vm)
                Screen.Settings -> SettingsScreen(vm)
                Screen.Help -> HelpScreen(vm)
            }
            vm.error?.let { Banner(it, isError = true, onDismiss = vm::dismissError, modifier = Modifier.align(Alignment.TopCenter)) }
            if (vm.busy) BusyOverlay(vm.busyMessage)
        }
    }
}

// --- Onboarding ------------------------------------------------------------

@Composable
private fun OnboardingScreen(vm: AppViewModel) = CenteredCard {
    var pw by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var reveal by remember { mutableStateOf(false) }

    BrandHeader("Set up your file vault", "Pick a master password. It encrypts every file and is never stored.")
    Spacer(Modifier.height(20.dp))
    PasswordField(pw, { pw = it }, "Master password", reveal) { reveal = !reveal }
    Spacer(Modifier.height(10.dp))
    StrengthMeter(pw)
    Spacer(Modifier.height(12.dp))
    PasswordField(confirm, { confirm = it }, "Confirm password", reveal) { reveal = !reveal }
    Spacer(Modifier.height(8.dp))
    if (confirm.isNotEmpty() && confirm != pw) {
        Text("Passwords don't match", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
    Spacer(Modifier.height(20.dp))
    Button(
        onClick = { vm.createVault(pw.toCharArray()) },
        enabled = pw.length >= 8 && pw == confirm,
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Create my vault") }
    Spacer(Modifier.height(12.dp))
    Text(
        "Zero-knowledge: if you lose both the password and recovery key, the data is unrecoverable.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
    )
    Spacer(Modifier.height(4.dp))
    OutlinedButton(onClick = vm::beginRestore, modifier = Modifier.fillMaxWidth()) {
        Text("Already have a vault? Restore from storage")
    }
    Spacer(Modifier.height(4.dp))
    TextButton(onClick = vm::openHelp, modifier = Modifier.fillMaxWidth()) { Text("Help & how it works") }
}

// --- Restore an existing vault (new device) --------------------------------

@Composable
private fun RestoreScreen(vm: AppViewModel) = CenteredCard(maxWidth = 520) {
    var showConnect by remember { mutableStateOf(false) }
    var remoteMenu by remember { mutableStateOf(false) }

    BrandHeader(
        "Restore an existing vault",
        "Point VaultMesh at the storage your other device syncs to. It downloads the encrypted vault; " +
            "then you unlock it with the same master password.",
    )
    Spacer(Modifier.height(20.dp))
    Button(onClick = { showConnect = true }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Outlined.Cloud, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Sign in to a cloud provider")
    }
    Spacer(Modifier.height(10.dp))
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { remoteMenu = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Use an already-connected remote")
        }
        DropdownMenu(expanded = remoteMenu, onDismissRequest = { remoteMenu = false }) {
            if (vm.configuredRemotes.isEmpty()) {
                DropdownMenuItem(text = { Text("No remotes configured yet") }, onClick = { remoteMenu = false }, enabled = false)
            } else {
                vm.configuredRemotes.forEach { r ->
                    DropdownMenuItem(text = { Text(r) }, onClick = { remoteMenu = false; vm.restoreFromRemote(r) })
                }
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    OutlinedButton(onClick = { FilePickers.pickDirectory()?.let(vm::restoreFromLocalFolder) }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Outlined.Folder, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Restore from a local or external folder")
    }
    Spacer(Modifier.height(16.dp))
    Text(
        "Tip: choose the same storage you connected on your first device. Your files stay encrypted — " +
            "the master password is what unlocks them here too.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
    )
    Spacer(Modifier.height(8.dp))
    TextButton(onClick = vm::cancelRestore, modifier = Modifier.fillMaxWidth()) { Text("Back") }

    if (showConnect) {
        ConnectProviderDialog(
            onConnect = { p, n, f -> showConnect = false; vm.connectAndRestore(p, n, f) },
            onDismiss = { showConnect = false },
        )
    }
}

// --- Unlock ----------------------------------------------------------------

@Composable
private fun UnlockScreen(vm: AppViewModel) = CenteredCard {
    var pw by remember { mutableStateOf("") }
    var reveal by remember { mutableStateOf(false) }
    var useRecovery by remember { mutableStateOf(false) }
    var recovery by remember { mutableStateOf("") }

    BrandHeader("Unlock VaultMesh", "Enter your master password to open your files.")
    Spacer(Modifier.height(20.dp))
    if (!useRecovery) {
        PasswordField(pw, { pw = it }, "Master password", reveal) { reveal = !reveal }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { vm.unlock(pw.toCharArray()) },
            enabled = pw.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Unlock") }
        TextButton(onClick = { useRecovery = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Use recovery key instead")
        }
    } else {
        OutlinedTextField(
            value = recovery,
            onValueChange = { recovery = it },
            label = { Text("Recovery key") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { vm.unlockWithRecovery(recovery) },
            enabled = recovery.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Unlock with recovery key") }
        TextButton(onClick = { useRecovery = false }, modifier = Modifier.fillMaxWidth()) {
            Text("Back to password")
        }
    }
    Spacer(Modifier.height(4.dp))
    TextButton(onClick = vm::openHelp, modifier = Modifier.fillMaxWidth()) { Text("Help & how it works") }
}

// --- Recovery key reveal ---------------------------------------------------

@Composable
private fun RecoveryRevealScreen(vm: AppViewModel) = CenteredCard(maxWidth = 560) {
    val clipboard = LocalClipboardManager.current
    var saved by remember { mutableStateOf(false) }
    val key = vm.recoveryKey ?: ""

    BrandHeader("Save your recovery key", "This is shown only once. It's the only way in if you forget your password.")
    Spacer(Modifier.height(20.dp))
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        SelectionContainer {
            Text(
                key,
                modifier = Modifier.padding(16.dp),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    OutlinedButton(onClick = { clipboard.setText(AnnotatedString(key)) }, modifier = Modifier.fillMaxWidth()) {
        Text("Copy to clipboard")
    }
    Spacer(Modifier.height(16.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = saved, onCheckedChange = { saved = it })
        Text("I have stored my recovery key somewhere safe")
    }
    Spacer(Modifier.height(12.dp))
    Button(onClick = vm::acknowledgeRecoveryKey, enabled = saved, modifier = Modifier.fillMaxWidth()) {
        Text("Continue to my files")
    }
}

// --- Files (the file manager) ----------------------------------------------

@Composable
private fun FilesScreen(vm: AppViewModel) {
    var query by remember { mutableStateOf("") }
    var showNewFolder by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<VaultTree.Node?>(null) }
    var moving by remember { mutableStateOf<VaultTree.Node?>(null) }
    var deleting by remember { mutableStateOf<VaultTree.Node?>(null) }

    Row(Modifier.fillMaxSize()) {
        Sidebar(vm)
        VerticalDivider()
        Column(Modifier.fillMaxHeight().weight(1f)) {
            Toolbar(
                vm = vm,
                query = query,
                onQuery = { query = it },
                onNewFolder = { showNewFolder = true },
            )
            if (vm.syncingNow || vm.autoSyncing) {
                LinearProgressIndicator(Modifier.fillMaxWidth().height(3.dp))
            }
            vm.statusMessage?.let {
                Text(
                    it,
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            HorizontalDivider()
            Row(Modifier.fillMaxSize()) {
                FileListArea(
                    vm = vm,
                    query = query,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    onRename = { renaming = it },
                    onMove = { moving = it },
                    onDelete = { deleting = it },
                )
                val selected = vm.selectedEntry()
                if (selected != null) {
                    VerticalDivider()
                    InfoPanel(
                        vm = vm,
                        entry = selected,
                        onRename = { node -> renaming = node },
                        onMove = { node -> moving = node },
                        onDelete = { node -> deleting = node },
                    )
                }
            }
        }
    }

    if (showNewFolder) {
        TextPromptDialog(
            title = "New folder",
            label = "Folder name",
            initial = "",
            confirmLabel = "Create",
            onDismiss = { showNewFolder = false },
            onConfirm = { vm.createFolder(it); showNewFolder = false },
        )
    }
    renaming?.let { node ->
        TextPromptDialog(
            title = "Rename",
            label = "New name",
            initial = node.name,
            confirmLabel = "Rename",
            onDismiss = { renaming = null },
            onConfirm = { vm.rename(node, it); renaming = null },
        )
    }
    moving?.let { node ->
        MoveDialog(vm = vm, node = node, onDismiss = { moving = null }, onConfirm = { dest ->
            vm.moveInto(node, dest); moving = null
        })
    }
    deleting?.let { node ->
        ConfirmDialog(
            title = if (node.isDir) "Delete folder?" else "Delete file?",
            message = if (node.isDir) {
                "\"${node.name}\" and everything inside it will be removed. The deletion syncs to your other devices."
            } else {
                "\"${node.name}\" will be removed. The deletion syncs to your other devices."
            },
            confirmLabel = "Delete",
            onDismiss = { deleting = null },
            onConfirm = { vm.delete(node); deleting = null },
        )
    }
    // While a storage window is open it owns the open dialog (avoids showing it in both windows).
    if (vm.explorerTarget == null) {
        vm.pendingOpen?.let { OpenChoiceDialog(it.node.name, onReadOnly = { vm.confirmOpen(false) }, onEdit = { vm.confirmOpen(true) }, onDismiss = vm::cancelOpen) }
    }
}

@Composable
private fun Sidebar(vm: AppViewModel) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.width(232.dp).fillMaxHeight()) {
        Column(Modifier.fillMaxHeight().padding(14.dp)) {
            Spacer(Modifier.height(titleBarInset))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource("icon.png"), contentDescription = null, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(8.dp))
                Text("VaultMesh", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(16.dp))
            SidebarItem(
                icon = Icons.Outlined.Folder,
                label = "My Files",
                selected = vm.currentDir.isEmpty(),
                onClick = { vm.navigateTo("") },
            )
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("STORAGE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), modifier = Modifier.weight(1f))
                if (vm.targets.any { it.enabled }) {
                    if (vm.storageUsageLoading) {
                        CircularProgressIndicator(Modifier.size(13.dp), strokeWidth = 1.5.dp)
                    } else {
                        IconButton(onClick = vm::refreshStorageUsage, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Outlined.Refresh, "Refresh capacity", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            // Single weighted, scrollable region so the action buttons below always pin to the bottom.
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (vm.targets.isEmpty()) {
                    Text(
                        "No storage connected — files are encrypted on this device only.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                } else {
                    vm.targets.forEach { t -> SidebarStorageItem(vm, t) }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Click a storage to browse what's on it.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = vm::openSettings, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Cloud, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Storage & sync")
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = vm::openSettings) { Icon(Icons.Outlined.Settings, "Settings") }
                IconButton(onClick = vm::openHelp) { Icon(Icons.Outlined.HelpOutline, "Help") }
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = vm::lock) {
                    Icon(Icons.Outlined.Lock, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Lock")
                }
            }
        }
    }
}

@Composable
private fun SidebarItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SidebarStorageItem(vm: AppViewModel, target: StorageTarget) {
    val usage = vm.storageUsage[target.id]
    val enabledAlpha = if (target.enabled) 1f else 0.4f
    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().clickable { vm.openExplorer(target) },
    ) {
        Column(Modifier.padding(horizontal = 6.dp, vertical = 5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (target.kind == TargetKind.RCLONE_REMOTE) Icons.Outlined.Cloud else Icons.Outlined.Folder,
                    null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = enabledAlpha),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    target.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = enabledAlpha * 0.9f),
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                vm.targetStatus[target.id]?.let {
                    val ok = it.startsWith("Synced")
                    Box(Modifier.size(8.dp).background(if (ok) SyncGreen else MaterialTheme.colorScheme.primary, CircleShape))
                }
            }
            usage?.let { u ->
                capacityFraction(u)?.let { frac ->
                    Spacer(Modifier.height(5.dp))
                    LinearProgressIndicator(
                        progress = { frac },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = if (frac > 0.9f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    )
                }
                capacityCaption(u)?.let { caption ->
                    Spacer(Modifier.height(3.dp))
                    Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
                }
            }
        }
    }
}

@Composable
private fun Toolbar(vm: AppViewModel, query: String, onQuery: (String) -> Unit, onNewFolder: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp + titleBarInset, bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = vm::navigateUp, enabled = vm.currentDir.isNotEmpty()) {
                Icon(Icons.Outlined.ArrowUpward, "Up")
            }
            // Breadcrumbs
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                val crumbs = vm.breadcrumbs()
                crumbs.forEachIndexed { i, (label, path) ->
                    if (i > 0) Icon(Icons.Outlined.ChevronRight, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    val isLast = i == crumbs.lastIndex
                    TextButton(onClick = { vm.navigateTo(path) }, enabled = !isLast) {
                        Text(label, style = MaterialTheme.typography.titleSmall, color = if (isLast) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary)
                    }
                }
            }
            OverallSyncChip(vm)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                placeholder = { Text("Search this folder") },
                singleLine = true,
                modifier = Modifier.weight(1f).height(52.dp),
            )
            Spacer(Modifier.width(10.dp))
            OutlinedButton(onClick = onNewFolder) {
                Icon(Icons.Outlined.CreateNewFolder, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("New folder")
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { FilePickers.pickFilesOrFolders().takeIf { it.isNotEmpty() }?.let(vm::addPaths) }) {
                Icon(Icons.Outlined.Add, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add")
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = vm::syncNow, enabled = vm.targets.any { it.enabled }) {
                Icon(Icons.Outlined.Sync, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Sync now")
            }
        }
    }
}

@Composable
private fun OverallSyncChip(vm: AppViewModel) {
    val state = vm.overallSyncState()
    val (label, color) = syncLabelColor(state)
    Surface(color = color.copy(alpha = 0.14f), shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            SyncGlyph(state, size = 16)
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = color)
        }
    }
}

@Composable
private fun FileListArea(
    vm: AppViewModel,
    query: String,
    modifier: Modifier,
    onRename: (VaultTree.Node) -> Unit,
    onMove: (VaultTree.Node) -> Unit,
    onDelete: (VaultTree.Node) -> Unit,
) {
    val nodes = vm.children.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
    Column(modifier) {
        // Column headers
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Name", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            Text("Size", Modifier.width(90.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            Text("Modified", Modifier.width(150.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            Text("Sync", Modifier.width(96.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
        HorizontalDivider()
        if (nodes.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.Folder, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f))
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (query.isNotBlank()) "Nothing matches \"$query\"." else "This folder is empty.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                    if (query.isBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text("Drag files here, or use Add / New folder above.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
                    }
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(nodes, key = { it.path }) { node ->
                    FileRow(vm, node, onRename, onMove, onDelete)
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                }
            }
        }
    }
}

@Composable
private fun FileRow(
    vm: AppViewModel,
    node: VaultTree.Node,
    onRename: (VaultTree.Node) -> Unit,
    onMove: (VaultTree.Node) -> Unit,
    onDelete: (VaultTree.Node) -> Unit,
) {
    val selected = vm.selectedPath == node.path
    val isConflict = node.entry?.conflictOf != null
    ContextMenuArea(items = {
        buildList {
            if (node.isDir) {
                add(ContextMenuItem("Open") { vm.navigateInto(node) })
            } else {
                add(ContextMenuItem("Open") { vm.requestOpen(node) })
                add(ContextMenuItem("Export (decrypt a copy)…") {
                    FilePickers.pickSaveDestination(node.name)?.let { vm.exportNode(node, it) }
                })
            }
            add(ContextMenuItem("Rename…") { onRename(node) })
            add(ContextMenuItem("Move to…") { onMove(node) })
            if (isConflict) {
                add(ContextMenuItem("Keep this conflict version") { node.entry?.let { vm.resolveConflict(it, true) } })
                add(ContextMenuItem("Discard this conflict copy") { node.entry?.let { vm.resolveConflict(it, false) } })
            }
            add(ContextMenuItem("Delete") { onDelete(node) })
        }
    }) {
        Surface(color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent) {
            Row(
                Modifier.fillMaxWidth()
                    .combinedClickable(
                        onClick = { vm.select(node.path) },
                        onDoubleClick = { vm.requestOpen(node) },
                    )
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (isConflict) Icons.Outlined.WarningAmber else nodeIcon(node),
                    null,
                    tint = when {
                        isConflict -> MaterialTheme.colorScheme.error
                        node.isDir -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                    },
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(node.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                    if (isConflict) {
                        Text("conflict copy of ${node.entry?.conflictOf}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
                Text(
                    if (node.isDir) "—" else formatSize(node.entry?.size ?: 0),
                    Modifier.width(90.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                )
                Text(
                    formatDate(node.entry?.mtimeEpochMs ?: 0),
                    Modifier.width(150.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                )
                Box(Modifier.width(96.dp)) { SyncBadge(vm.nodeSyncState(node)) }
            }
        }
    }
}

@Composable
private fun InfoPanel(
    vm: AppViewModel,
    entry: FileEntry,
    onRename: (VaultTree.Node) -> Unit,
    onMove: (VaultTree.Node) -> Unit,
    onDelete: (VaultTree.Node) -> Unit,
) {
    val node = remember(entry.path) {
        VaultTree.Node(entry.path.substringAfterLast('/'), entry.path, entry.isDir, entry)
    }
    Column(Modifier.width(300.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(nodeIcon(node), null, modifier = Modifier.size(40.dp), tint = if (node.isDir) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
            Spacer(Modifier.width(10.dp))
            Text(node.name, style = MaterialTheme.typography.titleMedium, maxLines = 2)
        }
        Spacer(Modifier.height(16.dp))
        InfoRow("Kind", if (node.isDir) "Folder" else (entry.path.substringAfterLast('.', "").uppercase().ifBlank { "File" } + " file"))
        InfoRow("Where", "/" + (entry.path.substringBeforeLast('/', "").ifBlank { "" }))
        if (!node.isDir) {
            InfoRow("Size", formatSize(entry.size))
            InfoRow("Chunks", "${entry.chunkIds.size} encrypted")
        } else {
            InfoRow("Items", "${VaultTree.descendantFiles(vm.entries, entry.path).size} file(s)")
        }
        InfoRow("Modified", formatDate(entry.mtimeEpochMs))
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Sync", Modifier.width(90.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            SyncBadge(vm.nodeSyncState(node))
        }

        Spacer(Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))
        if (!node.isDir) {
            Button(onClick = { vm.requestOpen(node) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.OpenInNew, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Open")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { FilePickers.pickSaveDestination(node.name)?.let { vm.exportNode(node, it) } }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.FileDownload, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Export a decrypted copy")
            }
            Spacer(Modifier.height(8.dp))
        }
        OutlinedButton(onClick = { onRename(node) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Edit, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Rename")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { onMove(node) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.DriveFileMove, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Move to…")
        }
        if (entry.conflictOf != null) {
            Spacer(Modifier.height(8.dp))
            Button(onClick = { vm.resolveConflict(entry, true) }, modifier = Modifier.fillMaxWidth()) { Text("Keep this version") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { vm.resolveConflict(entry, false) }, modifier = Modifier.fillMaxWidth()) { Text("Discard conflict copy") }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { onDelete(node) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.Delete, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(8.dp)); Text("Delete", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.padding(vertical = 4.dp)) {
        Text(label, Modifier.width(90.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        SelectionContainer { Text(value, style = MaterialTheme.typography.bodySmall) }
    }
}

// --- Sync badge ------------------------------------------------------------

private val SyncGreen = Color(0xFF22C55E)
private val SyncAmber = Color(0xFFF59E0B)

@Composable
private fun syncLabelColor(state: FileSyncState): Pair<String, Color> = when (state) {
    FileSyncState.LocalOnly -> "On this device" to MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    FileSyncState.Pending -> "Pending" to SyncAmber
    FileSyncState.Syncing -> "Syncing" to MaterialTheme.colorScheme.primary
    FileSyncState.Synced -> "Synced" to SyncGreen
    FileSyncState.Error -> "Sync error" to MaterialTheme.colorScheme.error
}

@Composable
private fun SyncGlyph(state: FileSyncState, size: Int) {
    when (state) {
        FileSyncState.Syncing -> CircularProgressIndicator(Modifier.size(size.dp), strokeWidth = 2.dp)
        FileSyncState.Synced -> Icon(Icons.Outlined.CloudDone, null, Modifier.size(size.dp), tint = SyncGreen)
        FileSyncState.Pending -> Icon(Icons.Outlined.CloudQueue, null, Modifier.size(size.dp), tint = SyncAmber)
        FileSyncState.Error -> Icon(Icons.Outlined.CloudOff, null, Modifier.size(size.dp), tint = MaterialTheme.colorScheme.error)
        FileSyncState.LocalOnly -> Icon(Icons.Outlined.Computer, null, Modifier.size(size.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
    }
}

@Composable
private fun SyncBadge(state: FileSyncState) {
    val (label, color) = syncLabelColor(state)
    Row(verticalAlignment = Alignment.CenterVertically) {
        SyncGlyph(state, size = 16)
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
    }
}

// --- Dialogs ---------------------------------------------------------------

@Composable
private fun TextPromptDialog(
    title: String,
    label: String,
    initial: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.replace("/", "") },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { Button(onClick = { onConfirm(text.trim()) }, enabled = text.isNotBlank()) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun MoveDialog(vm: AppViewModel, node: VaultTree.Node, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val folders = remember(vm.entries, node.path) { allFolderPaths(vm.entries).filterNot { it == node.path || it.startsWith("${node.path}/") } }
    var dest by remember { mutableStateOf(node.path.substringBeforeLast('/', "")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move \"${node.name}\" to") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                folders.forEach { f ->
                    val selected = f == dest
                    Surface(
                        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().clickable { dest = f },
                    ) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Folder, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(if (f.isEmpty()) "My Files (root)" else "/$f", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onConfirm(dest) }) { Text("Move here") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun OpenChoiceDialog(name: String, onReadOnly: () -> Unit, onEdit: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Open \"$name\"") },
        text = {
            Text(
                "VaultMesh will decrypt this file to a temporary location and open it in its associated app. " +
                    "Choose how to open it — temporary copies are wiped when you lock the vault.",
            )
        },
        confirmButton = {
            Button(onClick = onEdit) { Text("Open for editing") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onReadOnly) { Text("Read-only") }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

// --- Settings / replication ------------------------------------------------

@Composable
private fun SettingsScreen(vm: AppViewModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 24.dp + titleBarInset, bottom = 24.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = vm::closeSettings) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
            }
            Spacer(Modifier.width(4.dp))
            Text("Storage & sync", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Button(onClick = vm::syncNow, enabled = vm.targets.any { it.enabled }) {
                Icon(Icons.Outlined.Sync, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Sync now")
            }
        }
        Spacer(Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = vm.autoSyncEnabled, onCheckedChange = vm::setAutoSync)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Auto-sync", style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (vm.autoSyncEnabled) "Syncs automatically after changes and every few minutes." else "Sync only when you press Sync now.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            if (vm.autoSyncing) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp))
            } else {
                vm.lastSyncLabel?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
            }
        }
        Spacer(Modifier.height(16.dp))

        if (!vm.rcloneAvailable) {
            Surface(color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                Text(
                    "The rclone engine wasn't found. Folder and cloud mirroring need it; the packaged app bundles it automatically.",
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        Text("Storage targets", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        if (vm.targets.isEmpty()) {
            Text(
                "No targets yet. Encrypted copies of your files are pushed to every enabled target.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                vm.targets.forEach { target ->
                    TargetCard(
                        name = target.displayName,
                        subtitle = target.fsRoot,
                        isCloud = target.kind == TargetKind.RCLONE_REMOTE,
                        enabled = target.enabled,
                        status = vm.targetStatus[target.id],
                        usage = vm.storageUsage[target.id],
                        onBrowse = { vm.openExplorer(target) },
                        onToggle = { vm.setTargetEnabled(target.id, it) },
                        onRemove = { vm.removeTarget(target.id) },
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        Button(onClick = vm::openConnectDialog) {
            Icon(Icons.Outlined.Cloud, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Connect a storage provider")
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { FilePickers.pickDirectory()?.let(vm::addLocalFolderTarget) }) {
                Icon(Icons.Outlined.Folder, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add local mirror folder")
            }
            Spacer(Modifier.width(12.dp))
            var expanded by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { expanded = true }) {
                    Text("Use existing remote")
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    if (vm.configuredRemotes.isEmpty()) {
                        DropdownMenuItem(text = { Text("No rclone remotes configured yet") }, onClick = { expanded = false }, enabled = false)
                    } else {
                        vm.configuredRemotes.forEach { remote ->
                            DropdownMenuItem(text = { Text(remote) }, onClick = { vm.addCloudRemoteTarget(remote); expanded = false })
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Connect Google Drive, OneDrive, Dropbox, Box, pCloud, Yandex (browser sign-in) or Mega, S3, B2 " +
                "(credentials). Everything is encrypted before upload — providers only ever see ciphertext.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Linked folders", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (vm.linkedFolders.isNotEmpty()) {
                TextButton(onClick = vm::rescanLinkedFolders) { Text("Rescan now") }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "A linked folder is kept mirrored into the vault as it changes on disk — add, edit, or delete files and they sync.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            vm.linkedFolders.forEach { lf ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Folder, null, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(lf.prefix, style = MaterialTheme.typography.bodyLarge)
                            Text(lf.source, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                        TextButton(onClick = { vm.unlinkFolder(lf) }) { Text("Unlink") }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { FilePickers.pickDirectory()?.let(vm::linkFolder) }) {
            Icon(Icons.Outlined.Folder, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Link a folder")
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))
        Text("Storage maintenance", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Deleting or editing files leaves orphaned encrypted blocks behind in your local vault. " +
                "VaultMesh clears them automatically a few seconds after changes; you can also reclaim now. " +
                "It's safe — an object is re-fetched from a peer if it's ever needed again.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (vm.reclaimableObjects > 0) {
                    "${vm.reclaimableObjects} orphaned object(s) · ~${formatSize(vm.reclaimableBytes)} reclaimable"
                } else {
                    "Nothing to reclaim right now."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = vm::reclaimSpace) {
                Icon(Icons.Outlined.DeleteSweep, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Reclaim space")
            }
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))
        Text("Security", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        var showChangePw by remember { mutableStateOf(false) }
        var showRegen by remember { mutableStateOf(false) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { showChangePw = true }) {
                Icon(Icons.Outlined.Lock, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Change master password")
            }
            Spacer(Modifier.width(12.dp))
            OutlinedButton(onClick = { showRegen = true }) {
                Icon(Icons.Outlined.Shield, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Regenerate recovery key")
            }
        }

        if (vm.keychainSupported) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = vm.stayUnlockedEnabled, onCheckedChange = vm::setStayUnlocked)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Stay unlocked on this device", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (vm.stayUnlockedEnabled) {
                            "This device opens straight to your files. The key is held in your macOS Keychain. Use Lock to require the password again."
                        } else {
                            "Skip the master password on launch by keeping the key in your macOS Keychain. Convenience over at-rest secrecy — anyone with access to your unlocked Mac could open the vault."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            }
        }

        vm.statusMessage?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        }

        if (showChangePw) {
            ChangePasswordDialog(onDismiss = { showChangePw = false }, onConfirm = { vm.changeMasterPassword(it); showChangePw = false })
        }
        if (showRegen) {
            ConfirmDialog(
                title = "Regenerate recovery key?",
                message = "Your current recovery key will stop working immediately. You'll be shown a new one to save.",
                confirmLabel = "Regenerate",
                onDismiss = { showRegen = false },
                onConfirm = { showRegen = false; vm.regenerateRecoveryKey() },
            )
        }
    }

    if (vm.showConnectDialog) {
        ConnectProviderDialog(
            onConnect = { p, n, f -> vm.connectProvider(p, n, f) },
            onDismiss = vm::closeConnectDialog,
        )
    }
}

@Composable
private fun ChangePasswordDialog(onDismiss: () -> Unit, onConfirm: (CharArray) -> Unit) {
    var pw by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var reveal by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change master password") },
        text = {
            Column {
                PasswordField(pw, { pw = it }, "New password", reveal) { reveal = !reveal }
                Spacer(Modifier.height(8.dp))
                StrengthMeter(pw)
                Spacer(Modifier.height(10.dp))
                PasswordField(confirm, { confirm = it }, "Confirm new password", reveal) { reveal = !reveal }
                if (confirm.isNotEmpty() && confirm != pw) {
                    Text("Passwords don't match", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(pw.toCharArray()) }, enabled = pw.length >= 8 && pw == confirm) { Text("Change") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { Button(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ConnectProviderDialog(
    onConnect: (Provider, String, Map<String, String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var provider by remember { mutableStateOf(ProviderCatalog.all.first()) }
    var name by remember(provider) { mutableStateOf(provider.defaultRemoteName) }
    val fieldValues = remember(provider) { mutableStateMapOf<String, String>() }
    var providerMenu by remember { mutableStateOf(false) }

    val ready = name.isNotBlank() && provider.fields.all { (fieldValues[it.key] ?: "").isNotBlank() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Connect a storage provider") },
        text = {
            Column {
                Box {
                    OutlinedButton(onClick = { providerMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Cloud, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(provider.displayName, modifier = Modifier.weight(1f))
                        Text(if (provider.oauth) "browser sign-in" else "credentials", style = MaterialTheme.typography.bodySmall)
                    }
                    DropdownMenu(expanded = providerMenu, onDismissRequest = { providerMenu = false }) {
                        ProviderCatalog.all.forEach { p ->
                            DropdownMenuItem(text = { Text(p.displayName) }, onClick = { provider = p; providerMenu = false })
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name for this connection") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                provider.fields.forEach { field ->
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = fieldValues[field.key] ?: "",
                        onValueChange = { fieldValues[field.key] = it },
                        label = { Text(field.label) },
                        singleLine = true,
                        visualTransformation = if (field.password) PasswordVisualTransformation() else VisualTransformation.None,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    if (provider.oauth) {
                        "Connect opens your browser to sign in to ${provider.displayName}. Return here once it succeeds."
                    } else {
                        "Credentials are stored locally by rclone (passwords obscured) and never leave this machine in the clear."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConnect(provider, name.trim(), fieldValues.toMap()) }, enabled = ready) {
                Text("Connect")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun TargetCard(
    name: String,
    subtitle: String,
    isCloud: Boolean,
    enabled: Boolean,
    status: String?,
    usage: StorageUsage?,
    onBrowse: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (isCloud) Icons.Outlined.Cloud else Icons.Outlined.Folder, null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.bodyLarge)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                }
                Switch(checked = enabled, onCheckedChange = onToggle)
                IconButton(onClick = onRemove) { Icon(Icons.Outlined.Delete, contentDescription = "Remove") }
            }
            usage?.let { u ->
                capacityCaption(u)?.let { caption ->
                    Spacer(Modifier.height(10.dp))
                    capacityFraction(u)?.let { frac ->
                        LinearProgressIndicator(
                            progress = { frac },
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                            color = if (frac > 0.9f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onBrowse) {
                Icon(Icons.Outlined.Storage, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Browse storage")
            }
        }
    }
}

// --- Cloud storage explorer (dedicated window) -----------------------------

private enum class ExplorerMode { Files, Storage }

@Composable
fun StorageExplorerWindow(vm: AppViewModel, target: StorageTarget) {
    var mode by remember { mutableStateOf(ExplorerMode.Files) }
    var filesDir by remember { mutableStateOf("") }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(20.dp)) {
                // Header
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        if (target.kind == TargetKind.RCLONE_REMOTE) Icons.Outlined.Cloud else Icons.Outlined.Folder,
                        null,
                        modifier = Modifier.size(30.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(target.displayName, style = MaterialTheme.typography.titleLarge)
                        Text(
                            (if (target.kind == TargetKind.RCLONE_REMOTE) "Cloud remote · " else "Local mirror · ") + storageRootLabel(target),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                    if (mode == ExplorerMode.Storage) {
                        OutlinedButton(onClick = vm::refreshExplorer) {
                            Icon(Icons.Outlined.Refresh, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Refresh")
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))

                // Mode toggle: decrypted "Files" (default) vs raw "Storage contents".
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ExplorerTab("My files", Icons.Outlined.Description, mode == ExplorerMode.Files) { mode = ExplorerMode.Files }
                    Spacer(Modifier.width(8.dp))
                    ExplorerTab("Raw storage", Icons.Outlined.Storage, mode == ExplorerMode.Storage) { mode = ExplorerMode.Storage }
                }
                Spacer(Modifier.height(14.dp))

                when (mode) {
                    ExplorerMode.Files -> ExplorerFilesView(vm, target, filesDir, onDir = { filesDir = it })
                    ExplorerMode.Storage -> ExplorerRawView(vm)
                }
            }

            vm.error?.let { Banner(it, isError = true, onDismiss = vm::dismissError, modifier = Modifier.align(Alignment.TopCenter)) }
            vm.pendingOpen?.let { OpenChoiceDialog(it.node.name, onReadOnly = { vm.confirmOpen(false) }, onEdit = { vm.confirmOpen(true) }, onDismiss = vm::cancelOpen) }
            if (vm.busy) BusyOverlay(vm.busyMessage)
        }
    }
}

@Composable
private fun ExplorerTab(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.size(16.dp), tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** Decrypted, openable view of the files this storage holds (the vault tree). */
@Composable
private fun ColumnScope.ExplorerFilesView(vm: AppViewModel, target: StorageTarget, dir: String, onDir: (String) -> Unit) {
    // Breadcrumbs (root = the storage name).
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        IconButton(onClick = { onDir(dir.substringBeforeLast('/', "")) }, enabled = dir.isNotEmpty()) {
            Icon(Icons.Outlined.ArrowUpward, "Up")
        }
        val crumbs = remember(dir, target.displayName) {
            buildList {
                add(target.displayName to "")
                if (dir.isNotEmpty()) {
                    var acc = ""
                    dir.split('/').forEach { seg -> acc = if (acc.isEmpty()) seg else "$acc/$seg"; add(seg to acc) }
                }
            }
        }
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            crumbs.forEachIndexed { i, (label, path) ->
                if (i > 0) Icon(Icons.Outlined.ChevronRight, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                val isLast = i == crumbs.lastIndex
                TextButton(onClick = { onDir(path) }, enabled = !isLast) {
                    Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 1, color = if (isLast) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
    HorizontalDivider()
    val nodes = VaultTree.children(vm.entries, dir)
    Box(Modifier.weight(1f).fillMaxWidth()) {
        if (nodes.isEmpty()) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.Folder, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f))
                Spacer(Modifier.height(8.dp))
                Text("No files here yet.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(nodes, key = { it.path }) { node ->
                    ExplorerFileRow(
                        node = node,
                        onOpen = { if (node.isDir) onDir(node.path) else vm.requestOpen(node) },
                        onExport = { FilePickers.pickSaveDestination(node.name)?.let { vm.exportNode(node, it) } },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                }
            }
        }
    }
    vm.statusMessage?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))
    }
    Text(
        "Your files as backed up to ${target.displayName}. Double-click to open in its app, or export a decrypted copy. " +
            "They're stored encrypted — readable only here, after unlock.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun ExplorerFileRow(node: VaultTree.Node, onOpen: () -> Unit, onExport: () -> Unit) {
    ContextMenuArea(items = {
        buildList {
            add(ContextMenuItem(if (node.isDir) "Open" else "Open in app") { onOpen() })
            if (!node.isDir) add(ContextMenuItem("Export (decrypt a copy)…") { onExport() })
        }
    }) {
        Row(
            Modifier.fillMaxWidth()
                .combinedClickable(onClick = { if (node.isDir) onOpen() }, onDoubleClick = { if (!node.isDir) onOpen() })
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                nodeIcon(node),
                null,
                tint = if (node.isDir) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(node.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, modifier = Modifier.weight(1f))
            Text(
                if (node.isDir) "—" else formatSize(node.entry?.size ?: 0),
                Modifier.width(90.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            )
            if (node.isDir) {
                Icon(Icons.Outlined.ChevronRight, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
            } else {
                Spacer(Modifier.width(18.dp))
            }
        }
    }
}

/** Raw, transparency view: capacity + the actual encrypted blocks on the backend. */
@Composable
private fun ColumnScope.ExplorerRawView(vm: AppViewModel) {
    UsagePanel(vm)
    Spacer(Modifier.height(16.dp))

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        IconButton(onClick = vm::explorerUp, enabled = vm.explorerPath.isNotEmpty()) {
            Icon(Icons.Outlined.ArrowUpward, "Up")
        }
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            val crumbs = vm.explorerBreadcrumbs()
            crumbs.forEachIndexed { i, (label, path) ->
                if (i > 0) Icon(Icons.Outlined.ChevronRight, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                val isLast = i == crumbs.lastIndex
                TextButton(onClick = { vm.explorerNavigate(path) }, enabled = !isLast) {
                    Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 1, color = if (isLast) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary)
                }
            }
        }
        if (vm.explorerLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
    }
    Spacer(Modifier.height(6.dp))
    HorizontalDivider()

    Box(Modifier.weight(1f).fillMaxWidth()) {
        val error = vm.explorerError
        when {
            error != null -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.CloudOff, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(10.dp))
                Text("Couldn't read this storage", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = vm::refreshExplorer) { Text("Try again") }
            }
            vm.explorerEntries.isEmpty() && !vm.explorerLoading -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.Folder, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f))
                Spacer(Modifier.height(8.dp))
                Text("This folder is empty.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(vm.explorerEntries, key = { it.path }) { entry ->
                    RemoteEntryRow(entry, onOpen = { vm.explorerOpen(entry) })
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                }
            }
        }
    }
    Text(
        "The actual encrypted contents on this backend. Object names are content hashes and files are " +
            "ciphertext — your real names and folders live only in the encrypted index.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
        modifier = Modifier.padding(top = 10.dp),
    )
}

@Composable
private fun UsagePanel(vm: AppViewModel) {
    val usage = vm.explorerUsage
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Storage, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Capacity", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (!vm.explorerUsageLoaded) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            }
            when {
                !vm.explorerUsageLoaded -> {
                    Spacer(Modifier.height(8.dp))
                    Text("Checking available space…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
                usage == null || !usage.hasAny -> {
                    Spacer(Modifier.height(8.dp))
                    Text("This storage doesn't report capacity.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
                else -> {
                    Spacer(Modifier.height(12.dp))
                    capacityFraction(usage)?.let { frac ->
                        LinearProgressIndicator(
                            progress = { frac },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            color = if (frac > 0.9f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    Row(Modifier.fillMaxWidth()) {
                        usage.total?.let { UsageStat("Total", formatSize(it), Modifier.weight(1f)) }
                        usage.used?.let { UsageStat("Used", formatSize(it), Modifier.weight(1f)) }
                        usage.free?.let { UsageStat("Free", formatSize(it), Modifier.weight(1f)) }
                        usage.objects?.let { UsageStat("Objects", it.toString(), Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun UsageStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    }
}

@Composable
private fun RemoteEntryRow(entry: RemoteEntry, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (entry.isDir) Modifier.clickable(onClick = onOpen) else Modifier)
            .padding(horizontal = 8.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (entry.isDir) Icons.Outlined.Folder else Icons.Outlined.InsertDriveFile,
            null,
            modifier = Modifier.size(22.dp),
            tint = if (entry.isDir) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
        Spacer(Modifier.width(12.dp))
        Text(entry.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, modifier = Modifier.weight(1f))
        Text(
            if (entry.isDir) "—" else formatSize(entry.size),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            modifier = Modifier.width(96.dp),
        )
        if (entry.isDir) {
            Icon(Icons.Outlined.ChevronRight, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
        } else {
            Spacer(Modifier.width(18.dp))
        }
    }
}

private fun storageRootLabel(target: StorageTarget): String =
    if (target.kind == TargetKind.RCLONE_REMOTE) target.fsRoot.substringBefore(':') + ":" else target.fsRoot

// --- Shared pieces ---------------------------------------------------------

@Composable
private fun CenteredCard(maxWidth: Int = 420, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ElevatedCard(modifier = Modifier.widthIn(max = maxWidth.dp).padding(24.dp)) {
            Column(Modifier.padding(28.dp)) { content() }
        }
    }
}

@Composable
private fun BrandHeader(title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource("icon.png"), contentDescription = null, modifier = Modifier.size(32.dp))
        Spacer(Modifier.width(10.dp))
        Text("VaultMesh", style = MaterialTheme.typography.headlineSmall)
    }
    Spacer(Modifier.height(14.dp))
    Text(title, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(4.dp))
    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    reveal: Boolean,
    onToggleReveal: () -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = onToggleReveal) {
                Icon(if (reveal) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, contentDescription = "Toggle visibility")
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun StrengthMeter(password: String) {
    val score = passwordStrength(password)
    val (label, color) = when {
        password.isEmpty() -> "" to MaterialTheme.colorScheme.outline
        score < 0.4f -> "Weak" to MaterialTheme.colorScheme.error
        score < 0.75f -> "Fair" to Color(0xFFF59E0B)
        else -> "Strong" to MaterialTheme.colorScheme.primary
    }
    LinearProgressIndicator(
        progress = { score },
        modifier = Modifier.fillMaxWidth().height(6.dp),
        color = color,
    )
    if (label.isNotEmpty()) {
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = color)
    }
}

@Composable
private fun Banner(message: String, isError: Boolean, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.padding(16.dp).widthIn(max = 600.dp),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, color = Color.White, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("Dismiss", color = Color.White) }
        }
    }
}

@Composable
private fun BusyOverlay(message: String) {
    // Swallow clicks so an in-flight operation can't be triggered twice from underneath.
    val noop = remember { MutableInteractionSource() }
    Box(
        Modifier.fillMaxSize().background(Color(0x88000000))
            .clickable(interactionSource = noop, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        ElevatedCard {
            Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(16.dp))
                Text(message.ifEmpty { "Working…" })
            }
        }
    }
}

private fun passwordStrength(pw: String): Float {
    if (pw.isEmpty()) return 0f
    var score = 0
    if (pw.length >= 8) score++
    if (pw.length >= 12) score++
    if (pw.any { it.isDigit() }) score++
    if (pw.any { it.isLetter() && it.isUpperCase() } && pw.any { it.isLowerCase() }) score++
    if (pw.any { !it.isLetterOrDigit() }) score++
    return (score / 5f).coerceIn(0f, 1f)
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
    bytes < 1024L * 1024 * 1024 * 1024 -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
    else -> "%.2f TB".format(bytes / (1024.0 * 1024 * 1024 * 1024))
}

/** Fraction full (used/total) for a usage bar, or null if the backend didn't report enough. */
private fun capacityFraction(u: StorageUsage): Float? {
    val total = u.total ?: return null
    if (total <= 0) return null
    val used = u.used ?: u.free?.let { total - it } ?: return null
    return (used.toFloat() / total.toFloat()).coerceIn(0f, 1f)
}

/** One-line capacity caption ("X free of Y", or used / object count), or null if nothing to show. */
private fun capacityCaption(u: StorageUsage): String? = when {
    u.free != null && u.total != null -> "${formatSize(u.free!!)} free of ${formatSize(u.total!!)}"
    u.used != null && u.total != null -> "${formatSize(u.used!!)} of ${formatSize(u.total!!)} used"
    u.used != null -> "${formatSize(u.used!!)} used"
    u.objects != null -> "${u.objects} objects"
    else -> null
}

private val dateFmt = java.time.format.DateTimeFormatter
    .ofPattern("MMM d, yyyy  HH:mm")
    .withZone(java.time.ZoneId.systemDefault())

private fun formatDate(ms: Long): String =
    if (ms <= 0) "—" else dateFmt.format(java.time.Instant.ofEpochMilli(ms))

private fun nodeIcon(node: VaultTree.Node): ImageVector {
    if (node.isDir) return Icons.Outlined.Folder
    return when (node.name.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "svg", "tiff" -> Icons.Outlined.Photo
        "mp3", "wav", "flac", "aac", "ogg", "m4a" -> Icons.Outlined.AudioFile
        "mp4", "mov", "avi", "mkv", "webm", "wmv" -> Icons.Outlined.Movie
        "zip", "tar", "gz", "rar", "7z", "bz2", "xz" -> Icons.Outlined.FolderZip
        "txt", "md", "pdf", "doc", "docx", "rtf", "odt", "pages" -> Icons.Outlined.Description
        else -> Icons.Outlined.InsertDriveFile
    }
}

/** Every distinct folder path in the vault (including "" for root). */
private fun allFolderPaths(entries: List<FileEntry>): List<String> {
    val dirs = linkedSetOf("")
    entries.filterNot { it.deleted }.forEach { e ->
        val segs = e.path.split('/')
        val upTo = if (e.isDir) segs.size else segs.size - 1
        var acc = ""
        for (i in 0 until upTo) {
            acc = if (acc.isEmpty()) segs[i] else "$acc/${segs[i]}"
            dirs += acc
        }
    }
    return dirs.sortedBy { it.lowercase() }
}
