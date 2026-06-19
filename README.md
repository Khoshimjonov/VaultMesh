# VaultMesh

A cross-platform desktop app that encrypts your personal data under a master password and
keeps it synced across multiple storage backends at once (local disk, Google Drive, OneDrive,
Mega, Yandex Disk, Dropbox, S3, …). Zero-knowledge: storage providers only ever see ciphertext.

> Status: **Phases 0–3 complete** — a working local encrypted vault, replication to multiple
> storage backends via rclone, and two-way multi-device sync with conflict resolution.
> Remaining: more polish (background auto-sync/file watcher, in-app OAuth connect, delete
> propagation, virtual drive) — see Roadmap.

## What works today

- Create a vault protected by a master password (Argon2id) with a one-time recovery key.
- Add files/folders → split into chunks, deduplicated, encrypted (AES-256-GCM streaming).
- **Link a folder** to keep it mirrored into the vault as it changes on disk (adds/edits/deletes are
  reconciled on every sync, with a best-effort file watcher for immediacy).
- Browse the vault, export (decrypt) files, **delete** files (propagates as a tombstone), lock/unlock.
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
- **Gradle** (Kotlin DSL), multi-module. JVM toolchain 17.
- (Planned) **rclone** as the multi-provider transport engine.

## Modules

| Module | Responsibility |
|---|---|
| `core-crypto` | Argon2id KDF, envelope key hierarchy, recovery key, Tink AEAD/streaming, zeroize |
| `core-vault` | Chunking, content-addressed encrypted object store, encrypted manifest, version vectors, merge |
| `storage-spi` | `StorageEngine` transport interface + target model (keeps backends pluggable) |
| `storage-rclone` | rclone binary management, `rcd` daemon supervision, RC HTTP client, engine impl |
| `core-sync` | Parallel replication + two-way `SyncEngine` (version-vector merge, conflict copies) |
| `app-desktop` | Compose UI: onboarding, unlock, recovery-key reveal, vault browser, storage settings |

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

- **macOS**: open the `.dmg` and drag **VaultMesh** to Applications. The app is unsigned, so the
  first launch may need right-click → **Open** (or System Settings → Privacy & Security → Open Anyway).
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
3. **Add data**: add files, or *Settings → Linked folders → Link a folder* to keep a folder mirrored.
4. **Connect storage** (optional): *Settings → Connect a storage provider* → sign in (OAuth) or enter
   credentials. It's added as a sync target automatically.
5. **Sync**: press *Sync now*, or enable *Auto-sync*. On another device, install VaultMesh, connect the
   same provider, unlock with the same password — they converge (conflicts become conflict copies).

A full in-app guide is available via the **?** (Help) button on every screen.

## Security model (summary)

1. `KEK = Argon2id(masterPassword, salt)` — memory-hard (256 MiB / 3 passes by default).
2. A random 256-bit **Vault Master Key (VMK)** is generated once and wrapped under the KEK.
3. A 256-bit **recovery key** independently wraps the same VMK, so either can unlock.
4. Subkeys derived from the VMK via HKDF; file content encrypted with AES-256-GCM-HKDF streaming.
5. Only the wrapped keys + KDF params are persisted (`vault.json`). Nothing readable is stored.

If you lose **both** the password and recovery key, the data is unrecoverable — by design.

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
- **Remaining (Phase 4 packaging / polish)** — OneDrive drive-type selection, auto-update,
  biometric/keychain "stay unlocked", bundling rclone into installers, optional virtual mounted
  drive (FUSE/WinFsp/macFUSE), tombstone garbage-collection.

> Note: the OAuth *browser token exchange* itself is rclone's standard, well-tested flow and isn't
> auto-tested here (it needs a real provider account + browser). The `createRemote` → registered →
> usable-as-target path that drives it **is** covered by an integration test.

## How sync works (Phase 3)

Each device has a stable id; every file carries a version vector (deviceId → counter). On **Sync now**
the app, per shared target: pulls the peer's encrypted vault into a staging dir, imports any objects it
lacks (content-addressed, so safe), decrypts the peer manifest with the shared key, and merges:
fast-forward when one vector dominates; on concurrent edits with different content it keeps the local
file and writes the remote as a conflict copy. The merged manifest is pushed back so peers converge.
