package dev.vaultmesh.crypto

import kotlinx.serialization.Serializable

/**
 * Public-safe vault header, persisted as `vault.json` locally and on every remote.
 *
 * Contains NO secret material that is usable without the master password or recovery key:
 * only the wrapped (encrypted) Vault Master Key and the KDF parameters needed to re-derive
 * the wrapping keys. This is what makes the design zero-knowledge — a storage provider that
 * holds this file learns nothing about the contents.
 */
@Serializable
data class VaultHeader(
    val formatVersion: Int = CURRENT_FORMAT_VERSION,
    val vaultId: String,
    /** Symmetric content cipher identifier. */
    val cipher: String = Ciphers.AES256_GCM_HKDF_STREAMING,
    /** KDF parameters for deriving the password key-encryption-key (KEK). */
    val passwordKdf: KdfParams,
    /** KDF parameters for deriving the recovery key-encryption-key (RKEK). */
    val recoveryKdf: KdfParams,
    /** VMK encrypted under KEK(masterPassword). */
    val wrappedVmkPassword: WrappedKey,
    /** VMK encrypted under RKEK(recoveryKey). */
    val wrappedVmkRecovery: WrappedKey,
    val createdAt: Long,
) {
    companion object {
        const val CURRENT_FORMAT_VERSION = 1
    }
}

object Ciphers {
    const val AES256_GCM_HKDF_STREAMING = "AES256_GCM_HKDF_STREAMING"
}

/** Argon2id parameters. Stored in the header so the same key can be re-derived later. */
@Serializable
data class KdfParams(
    val algorithm: String = "argon2id",
    val memoryKib: Int,
    val iterations: Int,
    val parallelism: Int,
    val saltB64: String,
    val keyLenBytes: Int = 32,
) {
    companion object {
        /** Production defaults: 256 MiB, 3 passes, 4 lanes — strong against GPU/ASIC. */
        fun production(salt: ByteArray) = KdfParams(
            memoryKib = 256 * 1024,
            iterations = 3,
            parallelism = 4,
            saltB64 = B64.encode(salt),
        )

        /** Fast parameters for tests only — NEVER use for real vaults. */
        fun forTesting(salt: ByteArray) = KdfParams(
            memoryKib = 8 * 1024,
            iterations = 1,
            parallelism = 1,
            saltB64 = B64.encode(salt),
        )
    }
}

/** An AES-256-GCM ciphertext (12-byte IV prepended), Base64-encoded. */
@Serializable
data class WrappedKey(val ciphertextB64: String)
