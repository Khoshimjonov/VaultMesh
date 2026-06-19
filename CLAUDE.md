# VaultMesh — notes for Claude

Cross-platform desktop app: encrypt personal data under a master password and replicate/sync it
to multiple storage backends (local + cloud). Greenfield. See `README.md` for the user-facing
overview and `~/.claude/plans/wild-booping-conway.md` for the full approved architecture plan.

## Locked-in decisions (do not relitigate without asking)
- **Compose Multiplatform (Kotlin/JVM)** for the UI/runtime — chosen to match the owner's JVM background.
- **rclone** (embedded, driven via `rclone rcd` HTTP RC API) as the storage transport — Phase 2.
- **Multi-device two-way sync** is the target model (commit DAG + version vectors) — Phase 3.
- **Zero-knowledge + recovery key**: never store the password; one-time recovery key is the only backstop.

## Crypto invariants (core-crypto)
- KDF: Argon2id (BouncyCastle). Production params 256 MiB / 3 / 4; tests use `KdfParams.forTesting`.
- Envelope: random 256-bit VMK wrapped under both KEK(password) and RKEK(recovery key). Changing the
  password only re-wraps the VMK — never re-encrypt content.
- Content: Tink `subtle.AesGcmHkdfStreaming` (raw-key constructor `(ikm,"HmacSha256",32,segment,0)`).
  Key wrap: `subtle.AesGcmJce`. Subkeys: `subtle.Hkdf.computeHkdf("HMACSHA256", …)`.
  NOTE: Tink Java isn't in context7 — verify `subtle` signatures via `javap` on the resolved jar.
- Always zeroize key bytes (`ByteArray.wipe()`); `UnlockedVault` is `AutoCloseable`.

## Vault layout (core-vault) — identical on disk and on every remote
```
<root>/vault.json    public-safe header (wrapped keys + KDF params)
<root>/objects/<id>  encrypted chunks; id = HMAC(chunkIdKey, plaintext) hex → dedup
<root>/manifest.enc  encrypted file tree (paths/metadata never leak)
```
Chunking is fixed-size 1 MiB MVP (`Vault.CHUNK_SIZE`); FastCDC is a planned upgrade.

## Sync (Phase 3) invariants
- Every `FileEntry` has a `versionVector` (deviceId→counter); `Vault.addFile` bumps it. Device id is
  persisted via `DeviceId.loadOrCreate`; the app passes it to `VaultFactory` (tests pass "A"/"B").
- `ManifestMerger.merge` is PURE (unit-tested, no I/O): fast-forward when one vector dominates; on
  concurrent + different content, keep local and emit a conflict copy (`conflictOf` set) whose
  vector dominates both (so peers fast-forward, no re-conflict).
- Deletes are TOMBSTONES: `FileEntry.deleted=true` with empty chunks + bumped vector; `Vault.removeFile`
  creates them, `list()` hides them, they propagate like edits. Concurrent delete-vs-edit PRESERVES the
  edit (data-safe). `Vault.resolveConflict(path, keep)` keeps/discards a conflict copy then tombstones it.
- `Vault.changePassword` / `regenerateRecoveryKey` re-wrap the VMK and rewrite `vault.json` (header is a var).
- Linked folders: `Vault.syncFromSource(dir, prefix)` re-ingests changed files (size+mtime) and tombstones
  removed ones. App reconciles all linked folders at the start of every `performSync`; `FolderWatcher`
  (best-effort recursive `WatchService`) triggers a quiet reconcile+sync. Persisted in `linked-folders.tsv`.
- `SyncEngine.sync` (core-sync): pull peer vault → `importObjects` → `decryptManifest` (shared key) →
  merge → `applyMergedManifest` → push. Works because all devices of a vault share the master key.

## Storage transport
- `storage-spi`: `StorageEngine` (push/pull/reachable/listConfiguredRemotes) + `StorageTarget`.
- `storage-rclone`: drives a bundled `rclone rcd` (random localhost port + Basic auth) over the RC
  HTTP API. Replication/sync use `sync/copy` on the whole vault dir (rclone handles incremental,
  resumable, retried transfers). Verified against rclone v1.74 RC: `core/version`, `sync/copy`
  {srcFs,dstFs}, `operations/list` {fs,remote}→{list:[{Path,Name,Size,IsDir}]}, `config/listremotes`.
- Binary resolution `RcloneBinary.locate()`: -Dvaultmesh.rclone.path → $VAULTMESH_RCLONE →
  ~/.vaultmesh/bin/rclone → PATH. Dev: downloaded to `tools/rclone-bin/rclone` (gitignored); the
  test build + `:app-desktop:run` pass it via system property automatically.

## In-app provider connect (OAuth + credentials)
- `StorageEngine.createRemote(name,type,params)` → `RcloneRc.configCreate` posts `config/create`
  WITHOUT `nonInteractive`. For OAuth backends rclone then takes question defaults and runs its
  own browser auto-flow (opens browser + local redirect server on :53682, captures token, saves
  remote) — the RC call BLOCKS until the user finishes, so it runs on Dispatchers.IO with a long
  client timeout. For credential backends the params (keys/passwords) complete it immediately;
  rclone auto-obscures passwords. `ProviderCatalog` (app) lists providers + which need OAuth vs fields.
- The OAuth browser token exchange isn't auto-tested (needs a real account/browser); the
  createRemote→listConfiguredRemotes→use-as-target path is (`RcloneConfigTest`).

## Build / verify
- Gradle wrapper is committed (8.10.2). No system gradle. `java` = Temurin 17.
- `./gradlew test` (run after any crypto/vault change). `./gradlew :app-desktop:run` to launch.
- Version catalog: `gradle/libs.versions.toml` (Kotlin 2.1.0, Compose 1.7.3).
- Build-script gotcha: Compose desktop dep is `compose.desktop.currentOs` (lowercase `s`).

## Conventions
- Packages under `dev.vaultmesh.*`. Heavy crypto runs on `Dispatchers.Default`; Compose state is
  mutated only on `Dispatchers.Main` (see `AppViewModel`).
- Never log or persist plaintext, passwords, the VMK, or the recovery key.
