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
            "VaultMesh is a file manager for a private, distributed storage mesh. You browse folders and files like in Finder or Explorer, but everything is encrypted on your device and the same encrypted copy is kept in sync across all the storage you connect — your local disk plus cloud accounts like Google Drive, OneDrive, Dropbox, Mega, Yandex, S3 and more.",
            "It is zero-knowledge: everything is encrypted before it is uploaded. Storage providers only ever see scrambled data — never your file names, folder structure, or contents.",
            "Use it fully offline as a strong local encrypted file store, or connect any number of providers so your files are backed up and synced everywhere at once.",
        ),
    ),
    HelpSection(
        "Using the file manager",
        listOf(
            "• Browse: double-click a folder to open it; use the breadcrumb trail or the up arrow to go back. \"My Files\" in the sidebar is the top of your vault.",
            "• Open a file: double-click it (or select it and press Open). VaultMesh asks whether to open it read-only or for editing, then opens it in whatever app your system associates with that file type. If you open for editing and save, your changes are re-encrypted back into the vault automatically.",
            "• Add files: press Add, or simply drag files and folders from your computer straight into the window — they land in the folder you're viewing.",
            "• Organize: New folder creates a folder; right-click (or the info panel) lets you Rename, Move to another folder, Export a decrypted copy, or Delete. Select any item to see its details — size, type, modified date and sync status — in the info panel on the right.",
            "• Search: the search box filters the current folder by name.",
        ),
    ),
    HelpSection(
        "Sync status badges",
        listOf(
            "Every file and folder shows a small badge telling you where it stands:",
            "• Synced (green cloud) — present on every storage you've enabled, as of the last sync.",
            "• Pending (amber) — changed since the last sync and waiting to be uploaded.",
            "• Syncing (spinner) — being transferred right now. A thin progress bar appears at the top of the window and each storage shows its own status.",
            "• On this device (grey) — you haven't connected any storage yet, so the file lives only here.",
            "• Sync error (red) — the last attempt to reach a storage failed; it retries automatically. Folders summarize the worst state of anything inside them.",
        ),
    ),
    HelpSection(
        "How it works under the hood",
        listOf(
            "• Master password: your password is run through Argon2id (a deliberately slow, memory-hard function) to derive a key. The password itself is never stored.",
            "• Envelope encryption: a random Vault Master Key actually encrypts your data. Your password (and your recovery key) only wrap that key — so changing your password is instant and never re-encrypts your files.",
            "• File encryption: files are split into chunks, each encrypted with AES-256-GCM streaming encryption and stored under a content hash. Identical chunks are stored once (deduplication).",
            "• The whole encrypted store (a public header, the encrypted objects, and an encrypted index of your folders/files) is what gets mirrored to your storage targets — byte-for-byte identical everywhere.",
        ),
    ),
    HelpSection(
        "Getting started",
        listOf(
            "1. Create your vault and choose a strong master password. Use something long; a passphrase of several words is ideal.",
            "2. SAVE YOUR RECOVERY KEY. It is shown only once. Store it somewhere safe and separate from your password (a password manager, printed and locked away, etc.).",
            "3. You're in. Add files, create folders, and connect storage in Storage & sync.",
        ),
    ),
    HelpSection(
        "Linked folders",
        listOf(
            "• A linked folder (Storage & sync → Linked folders) keeps a folder on your disk continuously mirrored into the vault. When you add, change, or delete files in that folder, VaultMesh re-encrypts the changes on the next sync (and a built-in watcher notices changes quickly). This is ideal for folders you actively work in.",
            "• Export: any file can be decrypted back out to your disk via right-click → Export or the info panel.",
            "• Delete: removing a file or folder is remembered (as a tombstone) so the deletion also propagates to your other devices.",
        ),
    ),
    HelpSection(
        "Connecting storage providers",
        listOf(
            "Open Storage & sync → Connect a storage provider.",
            "• Browser sign-in (Google Drive, OneDrive, Dropbox, Box, pCloud, Yandex): click Connect and a browser window opens for you to sign in and authorize. VaultMesh never sees your provider password — the provider hands back an access token.",
            "• Credentials (Mega, Amazon S3, Backblaze B2): enter the keys/passwords the provider gave you. They are stored locally and obscured.",
            "Once connected, a provider becomes a sync target automatically. You can also add a plain local/external folder as a mirror target, or use a remote you configured outside the app.",
        ),
    ),
    HelpSection(
        "Syncing across devices",
        listOf(
            "Install VaultMesh on another device and choose 'Already have a vault? Restore from storage' on the welcome screen: point it at the SAME storage and unlock with the same master password. Your files download and the devices stay in sync. (See 'How do I connect another device' under Common questions.)",
            "• Sync now: the button in the toolbar (and in Storage & sync) pulls others' changes, merges, and pushes yours — to every enabled target.",
            "• Auto-sync (toggle in Storage & sync): syncs a few seconds after you make changes and polls periodically to pull others' changes, quietly in the background.",
            "• Conflicts: if the same file was changed on two devices at once, VaultMesh never overwrites — it keeps your version and saves the other as a conflict copy (marked with a warning icon). Right-click it, or use the info panel, to keep that version or discard the copy.",
        ),
    ),
    HelpSection(
        "Security & recovery",
        listOf(
            "• Change master password (Settings → Security): instantly re-wraps your key; your files are not re-encrypted and stay available.",
            "• Regenerate recovery key: issues a new key and invalidates the old one. You'll be shown the new key once — save it.",
            "• Stay unlocked on this device (macOS): an optional toggle in Settings → Security. When on, your key is kept in the macOS Keychain so the app opens straight to your files on this Mac without the master password — you're only asked when first creating or restoring a vault. It trades some at-rest secrecy for convenience: anyone who can use your unlocked Mac could open the vault. Turn it off (or press Lock) to require the password again. It's off by default and macOS-only.",
            "• Lock: clears your keys from memory. You'll need your password (or recovery key) to unlock again — even if 'Stay unlocked' is on, Lock requires it for the rest of this session.",
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
        "Reclaiming space (garbage collection)",
        listOf(
            "• When you delete or edit a file, the old encrypted blocks it used become orphaned — no longer referenced by anything. VaultMesh sweeps them out of your local vault automatically a few seconds after the change (and after a sync).",
            "• You can also reclaim on demand: Storage & sync → Storage maintenance → Reclaim space. It shows roughly how much is reclaimable first.",
            "• It's safe. Blocks are addressed by content hash, so if a swept block is ever needed again it's re-fetched from a device or remote that still has it. Only your LOCAL copy is compacted — connected remotes keep their copies (an additive design that never risks deleting a block another device just added).",
        ),
    ),
    HelpSection(
        "Browsing your storage & opening files from it",
        listOf(
            "• Capacity at a glance: each connected storage in the left sidebar shows a small bar and how much space is free of the total (for example '12 GB free of 15 GB'). Press the refresh icon next to STORAGE to re-check. The same readout appears on each target's card in Storage & sync.",
            "• Storage window: click a storage in the sidebar (or 'Browse storage' on its card in Storage & sync) to open a dedicated window for it. It has two tabs:",
            "    – My files: your real, decrypted files as backed up to that storage. Double-click to open one in its associated app, or right-click → Export to save a decrypted copy. This is the same open/edit flow as the main window — temporary copies are wiped when you lock.",
            "    – Raw storage: the actual contents on that backend for transparency — a 'vault.json' header, an 'objects' folder of hash-named encrypted blocks, and 'manifest.enc'. It's all ciphertext; your real names and folders live only in the encrypted index that VaultMesh decrypts locally.",
            "• Capacity comes from the provider itself (via the rclone engine). A few backends (for example plain S3 buckets) don't report quota — there the window says capacity isn't reported, which is normal.",
        ),
    ),
    HelpSection(
        "Common questions",
        listOf(
            "Why aren't my files in folders on the cloud — they're all in one folder of scrambled names? That's intentional, and it's exactly what keeps you private. On every storage VaultMesh keeps the same three things: a small public header (vault.json), an 'objects' folder of encrypted blocks named only by a content hash, and an encrypted index (manifest.enc). Your real folder names, file names and structure live ONLY inside that encrypted index — never exposed to the provider, which is why the cloud just shows one flat pile of ciphertext blocks. VaultMesh rebuilds your folder tree locally after decrypting the index. So it's not a bug; it's the zero-knowledge design working.",
            "How do I add a file? Any of these: drag files or folders from your computer into the window (they land in the folder you're viewing); press Add in the toolbar; or make a folder with New folder and add into it. You can also link a whole folder (Storage & sync → Linked folders) to keep it mirrored automatically. Added files are encrypted right away and uploaded on the next sync.",
            "Where are my local copies stored? Your local vault is at ~/.vaultmesh/default-vault — but it's stored ENCRYPTED (the same objects + index as the cloud), not as plain files. App data (your storage list, device id, settings, linked folders) sits under ~/.vaultmesh. When you open a file, a temporary decrypted copy is written under ~/.vaultmesh/open and wiped when you lock or quit. For a normal readable copy on disk, use Export.",
            "How do I connect another device and get my actual data? 1) Install VaultMesh on the other device. 2) On the welcome screen choose 'Already have a vault? Restore from storage'. 3) Point it at the SAME storage your first device uses (sign in to the same provider, pick an existing remote, or choose the same folder) — VaultMesh downloads the encrypted vault. 4) Unlock with the SAME master password. Your files appear and the device keeps syncing both ways. This works because your encryption key travels inside the vault, wrapped by your password, so the same password on any device recovers it. Do NOT pick 'Create my vault' on the second device — that makes a separate, empty vault.",
            "What happens when I add a file on one device? It's encrypted into your vault immediately and marked Pending. On the next sync (press Sync now, or automatically with Auto-sync on) it's uploaded to every enabled storage and the badge turns Synced. Your other devices pick it up the next time they sync. If two devices change the same file before syncing, VaultMesh keeps both — yours stays and the other becomes a clearly-labelled conflict copy. Nothing is silently overwritten.",
            "What does 'Add local mirror folder' do? It adds a plain folder on your disk — or an external/USB drive or a network share — as another sync target, so an extra encrypted copy of your whole vault is kept there, just like a cloud provider. Great for an offline backup or to hand-carry the vault to another machine. (This is different from a LINKED folder: a mirror RECEIVES an encrypted copy of the vault; a linked folder FEEDS your real files on disk into the vault.)",
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
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, top = 24.dp + titleBarInset, end = 24.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = vm::closeHelp) { Icon(Icons.Outlined.ArrowBack, contentDescription = "Back") }
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Outlined.Shield, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.width(26.dp).height(26.dp))
            Spacer(Modifier.width(8.dp))
            Text("VaultMesh — Help & guide", style = MaterialTheme.typography.titleLarge)
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Text(
                "A file manager for an encrypted, distributed storage mesh — browse and organize your files while encrypted copies stay synced across local disk and cloud storage. Private by design.",
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
