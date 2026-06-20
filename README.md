# VaultMesh

A cross-platform desktop **file manager for an encrypted, distributed storage mesh**. You browse
folders and files like in Finder/Explorer, but everything is encrypted on your device under a master
password and the same encrypted copy is kept in sync across all the storage you connect at once
(local disk, Google Drive, OneDrive, Mega, Yandex Disk, Dropbox, S3, …). Zero-knowledge: storage
providers only ever see ciphertext.

> Status: **Phases 0–4 complete** — a full encrypted file manager (folders, navigation, open-in-app,
> per-item sync status), replication to multiple storage backends via rclone, two-way multi-device
> sync with conflict resolution, in-app provider connect, storage capacity + a per-storage explorer,
> local garbage collection, opt-in stay-unlocked, and native installers with a bundled engine.
>
> ⚠️ Personal project, not independently security-audited. It uses vetted primitives (Google Tink,
> BouncyCastle Argon2id) but the composition has had no external review — keep an independent backup of
> anything you can't afford to lose. The packaged app is currently **unsigned** (see [Install](#install--run)).

## Contents

- [What works today](#what-works-today)
- [Tech stack](#tech-stack) · [Modules](#modules)
- [Install & run](#install--run) · [First-run flow](#first-run-flow)
- [Security model](#security-model-summary) · [Security caveats](#security-caveats)
- [How sync works](#how-sync-works-phase-3) · [Roadmap](#roadmap)
- [Credits & license](#credits--license)

## What works today

- **Browse like a file manager**: folders, breadcrumb navigation, search, a storage sidebar, and a
  details/info panel — all backed by an encrypted index (your folder structure never leaves the device).
- **Open files in their associated app**: double-click to open in Preview/Word/etc.; choose read-only
  or editable. Edits are re-encrypted back into the vault automatically; temp copies are wiped on lock.
  You can open files from the **per-storage window** too (its "My files" tab), not just the main view.
- **Stay unlocked (macOS, opt-in)**: keep the vault key in the macOS Keychain so the app opens straight
  to your files on this device — you're only asked for the master password when creating or restoring a
  vault. Off by default; trades some at-rest secrecy for convenience.
- **Full file management**: create folders, rename, move, delete (propagates as a tombstone), export a
  decrypted copy, and drag files in from your OS file manager.
- **Per-item sync badges**: every file/folder shows Synced / Pending / Syncing / On-this-device / Error,
  with a live progress bar during transfers.
- **Storage capacity + explorer**: the sidebar (and each target card) shows free/total space with a usage
  bar; click a storage to open a dedicated window that browses its real contents (the encrypted objects)
  with folder navigation and sizes — backed by rclone's `operations/about` and `operations/list`.
- **Garbage collection**: orphaned encrypted blocks left by deletes/edits are swept from the local vault
  automatically (debounced after changes and syncs), with a manual "Reclaim space" action — content-
  addressed, so a swept block is re-fetched from a peer if ever needed again.
- Create a vault protected by a master password (Argon2id) with a one-time recovery key.
- Files are split into chunks, deduplicated, and encrypted (AES-256-GCM streaming).
- **Link a folder** to keep it mirrored into the vault as it changes on disk (adds/edits/deletes are
  reconciled on every sync, with a best-effort file watcher for immediacy).
- Unlock with the master password **or** the recovery key.
- **Change the master password** (re-wraps the key, no content re-encryption) and **regenerate the
  recovery key** from the running app.
- **Resolve sync conflicts** in the browser: keep a conflict copy's version or discard it.
- **Connect storage providers in-app**: Google Drive, OneDrive, Dropbox, Box, pCloud, Yandex via
  browser sign-in (rclone's OAuth flow); Mega, S3, B2 via a credentials form. Add local mirror
  folders too. Connected providers become replication targets automatically.
- Replicate the encrypted vault to all enabled targets in parallel.
- **Two-way sync** across devices over a shared target: version-vector merge with automatic
  conflict copies (`name (conflict <device> <date>).ext`) — never loses data.
- **Auto-sync** (toggle in Settings): syncs a few seconds after you add files and polls every few
  minutes to pull other devices' changes — quietly, no modal interruptions.

## Tech stack

- **Kotlin / JVM** with **Compose Multiplatform** desktop UI (Material 3).
- **Google Tink** (`subtle` AEAD + streaming AEAD) + **BouncyCastle** (Argon2id).
- **rclone** as the multi-provider transport engine (bundled; driven via its `rcd` HTTP RC API).
- **Gradle** (Kotlin DSL), multi-module. JVM toolchain 17 (Temurin). Packaged with `jpackage`.

## Modules

| Module | Responsibility |
|---|---|
| `core-crypto` | Argon2id KDF, envelope key hierarchy, recovery key, Tink AEAD/streaming, zeroize |
| `core-vault` | Chunking, content-addressed encrypted object store, encrypted manifest, version vectors, merge |
| `storage-spi` | `StorageEngine` transport interface + target model (keeps backends pluggable) |
| `storage-rclone` | rclone binary management, `rcd` daemon supervision, RC HTTP client, engine impl |
| `core-sync` | Parallel replication + two-way `SyncEngine` (version-vector merge, conflict copies) |
| `app-desktop` | Compose file-manager UI: onboarding, unlock, recovery reveal, folder explorer (navigation, info panel, open-in-app, sync badges, drag-drop), storage & sync settings |

## Install & run

**Just use it (end user):** build a native installer and open it — no setup, nothing to preinstall
(the runtime and the rclone engine are bundled inside the app). Use the convenience script for your OS:

```bash
./scripts/package.sh     # macOS (.dmg) / Linux (.deb)
scripts\package.cmd      # Windows (.msi)
```

Or call Gradle directly:

```bash
./gradlew :app-desktop:packageDistributionForCurrentOS
# Output: app-desktop/build/compose/binaries/main/<dmg|msi|deb>/
```

- **macOS**: open the `.dmg` and drag **VaultMesh** to Applications. The app is **unsigned**, so
  Gatekeeper may block the first launch. Either right-click → **Open** (then **Open** in the dialog),
  or clear the quarantine flag once:

  ```bash
  xattr -dr com.apple.quarantine /Applications/VaultMesh.app
  ```

  Distributing to other people properly needs an Apple Developer ID (code-signing + notarization).
- **Windows**: run the `.msi`. **Linux**: install the `.deb`.

**Run from source (developer):**

```bash
./gradlew test              # run all unit/integration tests
./gradlew :app-desktop:run  # launch the desktop app (auto-downloads rclone on first build)
```

The default vault lives at `~/.vaultmesh/default-vault`; app config under `~/.vaultmesh`.

## First-run flow

1. **Create vault** → choose a strong master password.
2. **Save the recovery key** shown once — store it somewhere safe and separate.
3. **Add data**: drag files into the window, press *Add*, create folders with *New folder*, or
   *Storage & sync → Linked folders → Link a folder* to keep a folder mirrored.
4. **Open files**: double-click to open in the associated app (read-only or editable); edits are
   re-encrypted back automatically.
5. **Connect storage** (optional): *Storage & sync → Connect a storage provider* → sign in (OAuth) or
   enter credentials. It's added as a sync target automatically and each item shows a live sync badge.
6. **Sync**: press *Sync now*, or enable *Auto-sync*.
7. **Add another device**: install VaultMesh, choose *Already have a vault? Restore from storage* on the
   welcome screen, point it at the same storage, and unlock with the same master password — it downloads
   the encrypted vault and both devices stay in sync (conflicts become conflict copies, never overwrites).

A full in-app guide is available via the **?** (Help) button on every screen.

## Security model (summary)

1. `KEK = Argon2id(masterPassword, salt)` — memory-hard (256 MiB / 3 passes by default).
2. A random 256-bit **Vault Master Key (VMK)** is generated once and wrapped under the KEK.
3. A 256-bit **recovery key** independently wraps the same VMK, so either can unlock.
4. Subkeys derived from the VMK via HKDF; file content encrypted with AES-256-GCM-HKDF streaming.
5. Only the wrapped keys + KDF params are persisted (`vault.json`). Nothing readable is stored.

If you lose **both** the password and recovery key, the data is unrecoverable — by design.

**Opt-in "stay unlocked" (macOS):** you can choose to keep the VMK in the macOS Keychain so this device
opens without the master password (Settings → Security). It's off by default and trades at-rest secrecy
for convenience — anyone who can use your unlocked Mac could then open the vault. *Lock* still requires
the password for the rest of the session.

## Security caveats

Be honest with yourself about the threat model before trusting it with irreplaceable data:

- **Not independently audited.** The primitives are vetted (Tink AES-256-GCM streaming, BouncyCastle
  Argon2id), but this is single-author crypto plumbing. Keep a separate backup of critical data.
- **Unsigned builds.** No code-signing/notarization yet, so macOS Gatekeeper warns on first launch.
- **Local garbage collection only.** Reclaiming space compacts your *local* vault; connected remotes keep
  their copies (the additive sync model never risks deleting a block another device just added).
- **No auto-update.** Pull and rebuild to get fixes.
- **Metadata exposure.** Providers can't see your names or folders, but they do see the number and sizes
  of encrypted blocks.

## Roadmap

- **Phase 2 — done** — rclone transport; add replication targets; mirror encrypted vault to all in parallel.
- **Phase 3 — done (core)** — multi-device two-way sync: version vectors + conflict copies. Proven by a
  two-device integration test over a shared rclone remote.
- **In-app provider connect — done** — OAuth (browser) for Drive/OneDrive/Dropbox/Box/pCloud/Yandex
  and credential forms for Mega/S3/B2, driven through rclone's RC `config/create`.
- **Auto-sync — done** — Settings toggle; debounced sync after local changes + periodic poll, quiet
  (no modal overlay), starts on unlock / stops on lock.
- **Delete propagation + conflict resolution — done** — tombstones (delete-vs-edit preserves data),
  and a Resolve action (keep/discard) on conflict copies; covered by unit + two-device tests.
- **Password & recovery-key management — done** — change password, regenerate recovery key.
- **Linked folders + watcher — done** — `Vault.syncFromSource` reconciles a source folder (add/edit/
  delete) into the vault; reconciled on every sync, with a best-effort recursive `WatchService`.
- **Garbage collection — done (local)** — mark-and-sweep over content-addressed objects reclaims the
  orphaned blocks from deletes/edits/resolved conflicts; auto (debounced) + a manual "Reclaim space".
  Remote-side reclamation is intentionally deferred (rclone push is additive; pruning a remote is unsafe
  while a peer may have just added a block).
- **Stay unlocked — done (macOS, opt-in)** — keep the VMK in the macOS Keychain to skip the master
  password on launch; off by default, removed on disable or by *Lock* (which re-requires the password).
- **Storage capacity + explorer — done** — per-storage free/total readout and a dedicated window to
  browse a backend's real contents and open your files from it, via rclone `operations/about` / `list`.
- **Remaining (Phase 4 packaging / polish)** — code-signing + notarization (macOS), OneDrive drive-type
  selection, auto-update, biometric ("Touch ID") stay-unlocked, optional virtual mounted drive
  (FUSE/WinFsp/macFUSE), remote-side garbage collection.

> Note: the OAuth *browser token exchange* itself is rclone's standard, well-tested flow and isn't
> auto-tested here (it needs a real provider account + browser). The `createRemote` → registered →
> usable-as-target path that drives it **is** covered by an integration test.

## How sync works (Phase 3)

Each device has a stable id; every file carries a version vector (deviceId → counter). On **Sync now**
the app, per shared target: pulls the peer's encrypted vault into a staging dir, imports any objects it
lacks (content-addressed, so safe), decrypts the peer manifest with the shared key, and merges:
fast-forward when one vector dominates; on concurrent edits with different content it keeps the local
file and writes the remote as a conflict copy. The merged manifest is pushed back so peers converge.

## Credits & license

- **[rclone](https://rclone.org)** (MIT) — bundled per-OS as the storage transport; powers the 70+
  providers and incremental, resumable transfers.
- **[Google Tink](https://github.com/tink-crypto/tink-java)** (Apache-2.0) — AEAD + streaming AEAD.
- **[Bouncy Castle](https://www.bouncycastle.org/)** (MIT-style) — Argon2id KDF.
- **[Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/)** (Apache-2.0) — UI.

VaultMesh itself is released under the **[MIT License](LICENSE)** © 2026 Khoshimjonov. Third-party
components remain under their own licenses listed above.
