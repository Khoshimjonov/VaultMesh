package dev.vaultmesh.crypto

import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * An unlocked vault: holds the decrypted Vault Master Key (VMK) in memory and derives
 * purpose-specific subkeys/ciphers from it. Call [close] (or use it in a `use {}` block) to
 * zeroize the key material when locking.
 */
class UnlockedVault internal constructor(vmk: ByteArray) : AutoCloseable {
    private val vmkBytes: ByteArray = vmk
    private var closed = false

    /** Raw VMK — internal so callers go through derived subkeys instead. */
    internal val vmk: ByteArray
        get() {
            check(!closed) { "Vault is locked" }
            return vmkBytes
        }

    fun subKey(info: String, lengthBytes: Int = 32): ByteArray = SubKeys.derive(vmk, info, lengthBytes)

    /** Cipher for encrypting/decrypting file content chunks. */
    fun contentCipher(): ContentCipher = ContentCipher(subKey(SubKeys.CONTENT))

    /**
     * Returns a COPY of the raw VMK so the caller can stash it for an opt-in "stay unlocked" feature
     * (e.g. the OS keychain). Highly sensitive: anyone with this can decrypt the vault. The caller must
     * zeroize the copy when done. This is the only sanctioned way out of the module for the raw key.
     */
    fun exportKeyMaterial(): ByteArray = vmk.copyOf()

    override fun close() {
        if (!closed) {
            vmkBytes.wipe()
            closed = true
        }
    }
}

/** Result of creating a brand-new vault. The recovery key is shown to the user exactly once. */
data class VaultCreation(
    val header: VaultHeader,
    val recoveryKey: String,
    val unlocked: UnlockedVault,
)

/**
 * Top-level crypto API for a vault's lifecycle: create, unlock (password or recovery), and
 * change password. Zero-knowledge — only the wrapped VMK is ever persisted.
 */
object VaultCrypto {
    val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    /** Associated data binds a wrapped key to its specific vault, preventing cross-vault splicing. */
    private fun aad(vaultId: String): ByteArray = "vaultmesh-vmk-wrap:$vaultId".toByteArray(Charsets.UTF_8)

    fun createVault(
        password: CharArray,
        kdfFactory: (ByteArray) -> KdfParams = { KdfParams.production(it) },
    ): VaultCreation {
        val vaultId = UUID.randomUUID().toString()
        val vmk = randomBytes(32)
        val recoverySecret = randomBytes(32)
        val recoveryKey = RecoveryKey.encode(recoverySecret)

        val passwordKdf = kdfFactory(randomBytes(16))
        val recoveryKdf = kdfFactory(randomBytes(16))
        val kek = Argon2Kdf.deriveKey(password, passwordKdf)
        val rkek = Argon2Kdf.deriveKey(recoverySecret, recoveryKdf)
        try {
            val aad = aad(vaultId)
            val header = VaultHeader(
                vaultId = vaultId,
                passwordKdf = passwordKdf,
                recoveryKdf = recoveryKdf,
                wrappedVmkPassword = KeyWrap.wrap(kek, vmk, aad),
                wrappedVmkRecovery = KeyWrap.wrap(rkek, vmk, aad),
                createdAt = System.currentTimeMillis(),
            )
            return VaultCreation(header, recoveryKey, UnlockedVault(vmk.copyOf()))
        } finally {
            kek.wipe(); rkek.wipe(); recoverySecret.wipe(); vmk.wipe()
        }
    }

    fun unlockWithPassword(header: VaultHeader, password: CharArray): UnlockedVault {
        val kek = Argon2Kdf.deriveKey(password, header.passwordKdf)
        try {
            return UnlockedVault(KeyWrap.unwrap(kek, header.wrappedVmkPassword, aad(header.vaultId)))
        } finally {
            kek.wipe()
        }
    }

    /**
     * Rebuilds an [UnlockedVault] from raw VMK bytes previously obtained via
     * [UnlockedVault.exportKeyMaterial] — used by the opt-in "stay unlocked" path to open the vault
     * without re-deriving from the password. No password/KDF work happens here.
     */
    fun unlockWithKeyMaterial(vmk: ByteArray): UnlockedVault {
        require(vmk.size == 32) { "VMK must be 32 bytes" }
        return UnlockedVault(vmk.copyOf())
    }

    fun unlockWithRecovery(header: VaultHeader, recoveryKey: String): UnlockedVault {
        val secret = RecoveryKey.decode(recoveryKey)
        val rkek = Argon2Kdf.deriveKey(secret, header.recoveryKdf)
        try {
            return UnlockedVault(KeyWrap.unwrap(rkek, header.wrappedVmkRecovery, aad(header.vaultId)))
        } finally {
            rkek.wipe(); secret.wipe()
        }
    }

    /** Re-wraps the VMK under a new password without touching encrypted content. */
    fun changePassword(
        header: VaultHeader,
        unlocked: UnlockedVault,
        newPassword: CharArray,
        kdfFactory: (ByteArray) -> KdfParams = { KdfParams.production(it) },
    ): VaultHeader {
        val newKdf = kdfFactory(randomBytes(16))
        val kek = Argon2Kdf.deriveKey(newPassword, newKdf)
        try {
            return header.copy(
                passwordKdf = newKdf,
                wrappedVmkPassword = KeyWrap.wrap(kek, unlocked.vmk, aad(header.vaultId)),
            )
        } finally {
            kek.wipe()
        }
    }

    /** Issues a fresh recovery key, re-wrapping the VMK under it. The old recovery key stops working. */
    fun regenerateRecoveryKey(
        header: VaultHeader,
        unlocked: UnlockedVault,
        kdfFactory: (ByteArray) -> KdfParams = { KdfParams.production(it) },
    ): Pair<VaultHeader, String> {
        val recoverySecret = randomBytes(32)
        val recoveryKey = RecoveryKey.encode(recoverySecret)
        val recoveryKdf = kdfFactory(randomBytes(16))
        val rkek = Argon2Kdf.deriveKey(recoverySecret, recoveryKdf)
        try {
            val newHeader = header.copy(
                recoveryKdf = recoveryKdf,
                wrappedVmkRecovery = KeyWrap.wrap(rkek, unlocked.vmk, aad(header.vaultId)),
            )
            return newHeader to recoveryKey
        } finally {
            rkek.wipe(); recoverySecret.wipe()
        }
    }

    fun serializeHeader(header: VaultHeader): String =
        json.encodeToString(VaultHeader.serializer(), header)

    fun parseHeader(text: String): VaultHeader =
        json.decodeFromString(VaultHeader.serializer(), text)
}
