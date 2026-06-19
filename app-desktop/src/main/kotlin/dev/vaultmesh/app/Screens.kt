package dev.vaultmesh.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
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
import dev.vaultmesh.storage.TargetKind
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun AppRoot(vm: AppViewModel) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            when (vm.screen) {
                Screen.Onboarding -> OnboardingScreen(vm)
                Screen.Unlock -> UnlockScreen(vm)
                Screen.RecoveryReveal -> RecoveryRevealScreen(vm)
                Screen.Browser -> BrowserScreen(vm)
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

    BrandHeader("Create your vault", "Pick a master password. It encrypts everything and is never stored.")
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
    ) { Text("Create encrypted vault") }
    Spacer(Modifier.height(12.dp))
    Text(
        "Zero-knowledge: if you lose both the password and recovery key, the data is unrecoverable.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
    )
    Spacer(Modifier.height(4.dp))
    TextButton(onClick = vm::openHelp, modifier = Modifier.fillMaxWidth()) { Text("Help & how it works") }
}

// --- Unlock ----------------------------------------------------------------

@Composable
private fun UnlockScreen(vm: AppViewModel) = CenteredCard {
    var pw by remember { mutableStateOf("") }
    var reveal by remember { mutableStateOf(false) }
    var useRecovery by remember { mutableStateOf(false) }
    var recovery by remember { mutableStateOf("") }

    BrandHeader("Unlock VaultMesh", "Enter your master password to decrypt your vault.")
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
        Text("Continue to my vault")
    }
}

// --- Browser ---------------------------------------------------------------

@Composable
private fun BrowserScreen(vm: AppViewModel) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Shield, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("VaultMesh", style = MaterialTheme.typography.titleLarge)
                Text(vm.vaultPath, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            IconButton(onClick = vm::openHelp) {
                Icon(Icons.Outlined.HelpOutline, contentDescription = "Help")
            }
            IconButton(onClick = vm::openSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = "Storage settings")
            }
            Spacer(Modifier.width(4.dp))
            OutlinedButton(onClick = vm::lock) {
                Icon(Icons.Outlined.Lock, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Lock")
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = { FilePickers.pickFilesOrFolders().takeIf { it.isNotEmpty() }?.let(vm::addFiles) }) {
            Icon(Icons.Outlined.Add, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Add files or folder")
        }
        vm.statusMessage?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(16.dp))
        if (vm.files.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Your vault is empty. Add files or a folder to encrypt them.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(vm.files) { entry ->
                    val isConflict = entry.conflictOf != null
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (isConflict) Icons.Outlined.WarningAmber else Icons.Outlined.Description,
                                null,
                                tint = if (isConflict) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(22.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(entry.path, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    if (isConflict) "conflict copy of ${entry.conflictOf}" else formatSize(entry.size) + " • encrypted",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                )
                            }
                            if (isConflict) {
                                var resolveMenu by remember { mutableStateOf(false) }
                                Box {
                                    TextButton(onClick = { resolveMenu = true }) { Text("Resolve") }
                                    DropdownMenu(expanded = resolveMenu, onDismissRequest = { resolveMenu = false }) {
                                        DropdownMenuItem(text = { Text("Keep this version") }, onClick = { vm.resolveConflict(entry, true); resolveMenu = false })
                                        DropdownMenuItem(text = { Text("Discard this copy") }, onClick = { vm.resolveConflict(entry, false); resolveMenu = false })
                                    }
                                }
                            }
                            IconButton(onClick = {
                                val name = entry.path.substringAfterLast('/')
                                FilePickers.pickSaveDestination(name)?.let { vm.exportFile(entry, it) }
                            }) {
                                Icon(Icons.Outlined.FileDownload, contentDescription = "Export")
                            }
                            IconButton(onClick = { vm.deleteFile(entry) }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Delete")
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- Settings / replication ------------------------------------------------

@Composable
private fun SettingsScreen(vm: AppViewModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
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

        Text("Replication targets", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        if (vm.targets.isEmpty()) {
            Text(
                "No targets yet. Encrypted copies of your vault are pushed to every enabled target.",
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

    if (vm.showConnectDialog) ConnectProviderDialog(vm)
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
private fun ConnectProviderDialog(vm: AppViewModel) {
    var provider by remember { mutableStateOf(ProviderCatalog.all.first()) }
    var name by remember(provider) { mutableStateOf(provider.defaultRemoteName) }
    val fieldValues = remember(provider) { mutableStateMapOf<String, String>() }
    var providerMenu by remember { mutableStateOf(false) }

    val ready = name.isNotBlank() && provider.fields.all { (fieldValues[it.key] ?: "").isNotBlank() }

    AlertDialog(
        onDismissRequest = vm::closeConnectDialog,
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
            Button(onClick = { vm.connectProvider(provider, name.trim(), fieldValues.toMap()) }, enabled = ready) {
                Text("Connect")
            }
        },
        dismissButton = { TextButton(onClick = vm::closeConnectDialog) { Text("Cancel") } },
    )
}

@Composable
private fun TargetCard(
    name: String,
    subtitle: String,
    isCloud: Boolean,
    enabled: Boolean,
    status: String?,
    onToggle: (Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
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
    }
}

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
        Icon(Icons.Outlined.Shield, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
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
    else -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
}
