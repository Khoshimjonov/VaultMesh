package dev.vaultmesh.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class HelpSection(val title: String, val body: List<String>)

private val helpSections = listOf(
    HelpSection(
        "What is VaultMesh?",
        listOf(
            "VaultMesh is a personal vault that encrypts your files on your computer and keeps encrypted copies synced across the storage you choose — your local disk plus cloud accounts like Google Drive, OneDrive, Dropbox, Mega, Yandex, S3 and more.",
            "It is zero-knowledge: everything is encrypted on your device before it is uploaded. Storage providers only ever see scrambled data — never your file names, folder structure, or contents.",
            "You can use it fully offline with just local encryption, or connect any number of providers for backup and multi-device sync.",
        ),
    ),
    HelpSection(
        "How it works",
        listOf(
            "• Master password: when you create a vault, your password is run through Argon2id (a deliberately slow, memory-hard function) to derive a key. The password itself is never stored.",
            "• Envelope encryption: a random Vault Master Key actually encrypts your data. Your password (and your recovery key) only wrap that key — so changing your password is instant and never re-encrypts your files.",
            "• File encryption: files are split into chunks and each chunk is encrypted with AES-256-GCM streaming encryption, then stored under a content hash. Identical chunks are stored once (deduplication).",
            "• The whole encrypted vault (a public header, the encrypted objects, and an encrypted index) is what gets mirrored to your storage targets — byte-for-byte identical everywhere.",
        ),
    ),
    HelpSection(
        "Getting started",
        listOf(
            "1. Create your vault and choose a strong master password. Use something long; a passphrase of several words is ideal.",
            "2. SAVE YOUR RECOVERY KEY. It is shown only once. Store it somewhere safe and separate from your password (a password manager, printed and locked away, etc.).",
            "3. You're in. Add files or link a folder, and optionally connect storage providers in Settings.",
        ),
    ),
    HelpSection(
        "Adding your data",
        listOf(
            "• Add files or folder: use the button on the main screen to encrypt files or a whole folder into the vault once.",
            "• Link a folder (Settings → Linked folders): keeps a folder on your disk continuously mirrored into the vault. When you add, change, or delete files in that folder, VaultMesh re-encrypts the changes on the next sync (and a built-in watcher notices changes quickly). This is the best option for folders you actively work in.",
            "• Export: click the download icon on any file to decrypt a copy back out to your disk.",
            "• Delete: the trash icon removes a file. The deletion is remembered so it also propagates to your other devices.",
        ),
    ),
    HelpSection(
        "Connecting storage providers",
        listOf(
            "Open Settings → Connect a storage provider.",
            "• Browser sign-in (Google Drive, OneDrive, Dropbox, Box, pCloud, Yandex): click Connect and a browser window opens for you to sign in and authorize. VaultMesh never sees your provider password — the provider hands back an access token.",
            "• Credentials (Mega, Amazon S3, Backblaze B2): enter the keys/passwords the provider gave you. They are stored locally and obscured.",
            "Once connected, a provider becomes a sync target automatically. You can also add a plain local/external folder as a mirror target, or use a remote you configured outside the app.",
        ),
    ),
    HelpSection(
        "Syncing across devices",
        listOf(
            "Install VaultMesh on another device, connect the SAME storage provider, and unlock with the same master password. Your vaults will converge.",
            "• Sync now: pulls others' changes, merges, and pushes yours — to every enabled target.",
            "• Auto-sync (toggle in Settings): syncs a few seconds after you make changes and polls periodically to pull others' changes, quietly in the background.",
            "• Conflicts: if the same file was changed on two devices at once, VaultMesh never overwrites — it keeps your version and saves the other as 'name (conflict <device> <date>).ext'. Use the Resolve button to keep one version or discard the copy.",
        ),
    ),
    HelpSection(
        "Security & recovery",
        listOf(
            "• Change master password (Settings → Security): instantly re-wraps your key; your files are not re-encrypted and stay available.",
            "• Regenerate recovery key: issues a new key and invalidates the old one. You'll be shown the new key once — save it.",
            "• Lock: clears your keys from memory. You'll need your password (or recovery key) to unlock again.",
            "• Zero-knowledge means there is no 'forgot password' email. If you lose BOTH your master password AND your recovery key, your data cannot be recovered by anyone, including you. This is the price of true privacy — keep your recovery key safe.",
        ),
    ),
    HelpSection(
        "Where your data lives",
        listOf(
            "• Your local vault: ~/.vaultmesh/default-vault (encrypted objects + a public header + an encrypted index).",
            "• App settings, device id, linked-folder list, and rclone remote configs live under ~/.vaultmesh.",
            "• On each connected provider, an encrypted copy is stored in a 'VaultMesh' folder. Opening it shows only opaque files — nothing readable.",
        ),
    ),
    HelpSection(
        "Troubleshooting & FAQ",
        listOf(
            "• 'rclone engine not found': the installed app bundles it automatically; this only appears in unusual setups. Reinstalling fixes it.",
            "• A provider sign-in didn't finish: just try Connect again; an interrupted browser authorization is safe to retry.",
            "• Sync shows an error on a target: check that device is online and the provider is still authorized, then Sync now again. Other targets are unaffected.",
            "• Is my password sent anywhere? No. Neither your password nor your keys ever leave your device.",
            "• Can I use it with no cloud? Yes — with no targets it's simply a strong local encrypted vault.",
        ),
    ),
)

@Composable
internal fun HelpScreen(vm: AppViewModel) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(24.dp, 24.dp, 24.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = vm::closeHelp) { Icon(Icons.Outlined.ArrowBack, contentDescription = "Back") }
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Outlined.Shield, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.width(26.dp).height(26.dp))
            Spacer(Modifier.width(8.dp))
            Text("VaultMesh — Help & guide", style = MaterialTheme.typography.titleLarge)
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Text(
                "Encrypt your files and sync encrypted copies across local disk and cloud storage. Private by design.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(20.dp))
            helpSections.forEach { section ->
                Text(section.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                section.body.forEach { paragraph ->
                    Text(paragraph, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.height(16.dp))
            }
            Surface(color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Remember: lose both your master password and recovery key and the data is gone for good. Back up your recovery key now if you haven't.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}
