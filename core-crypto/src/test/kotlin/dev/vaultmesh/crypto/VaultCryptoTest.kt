package dev.vaultmesh.crypto

import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Fast KDF for tests so we don't burn 256 MiB / 3 passes per derivation. */
private val testKdf: (ByteArray) -> KdfParams = { KdfParams.forTesting(it) }

class VaultCryptoTest {

    private val password = "correct horse battery staple".toCharArray()
    private val aad = "chunk-0001".toByteArray()
    private val plaintext = "VaultMesh — secret personal data 🔐".toByteArray(Charsets.UTF_8)

    private fun encrypt(vault: UnlockedVault, data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        vault.contentCipher().encryptingStream(out, aad).use { it.write(data) }
        return out.toByteArray()
    }

    private fun decrypt(vault: UnlockedVault, ciphertext: ByteArray): ByteArray =
        vault.contentCipher().decryptingStream(ByteArrayInputStream(ciphertext), aad).use { it.readBytes() }

    @Test
    fun `content round-trips through streaming AEAD`() {
        val creation = VaultCrypto.createVault(password, testKdf)
        creation.unlocked.use { vault ->
            val ct = encrypt(vault, plaintext)
            assertTrue(ct.size > plaintext.size, "ciphertext should carry header+tag overhead")
            assertContentEquals(plaintext, decrypt(vault, ct))
        }
    }

    @Test
    fun `unlock with password recovers the same VMK`() {
        val creation = VaultCrypto.createVault(password, testKdf)
        val ct = creation.unlocked.use { encrypt(it, plaintext) }

        VaultCrypto.unlockWithPassword(creation.header, password).use { vault ->
            assertContentEquals(plaintext, decrypt(vault, ct))
        }
    }

    @Test
    fun `unlock with recovery key recovers the same VMK`() {
        val creation = VaultCrypto.createVault(password, testKdf)
        val ct = creation.unlocked.use { encrypt(it, plaintext) }

        VaultCrypto.unlockWithRecovery(creation.header, creation.recoveryKey).use { vault ->
            assertContentEquals(plaintext, decrypt(vault, ct))
        }
    }

    @Test
    fun `wrong password fails cleanly`() {
        val creation = VaultCrypto.createVault(password, testKdf)
        assertFailsWith<WrongCredentialsException> {
            VaultCrypto.unlockWithPassword(creation.header, "wrong password".toCharArray())
        }
    }

    @Test
    fun `password change keeps existing content readable`() {
        val creation = VaultCrypto.createVault(password, testKdf)
        val ct = creation.unlocked.use { encrypt(it, plaintext) }
        val newPassword = "a-much-longer-new-passphrase".toCharArray()

        val updated = VaultCrypto.unlockWithPassword(creation.header, password).use { vault ->
            VaultCrypto.changePassword(creation.header, vault, newPassword, testKdf)
        }

        assertFailsWith<WrongCredentialsException> {
            VaultCrypto.unlockWithPassword(updated, password)
        }
        VaultCrypto.unlockWithPassword(updated, newPassword).use { vault ->
            assertContentEquals(plaintext, decrypt(vault, ct))
        }
        // Recovery key still works after a password change.
        VaultCrypto.unlockWithRecovery(updated, creation.recoveryKey).use { vault ->
            assertContentEquals(plaintext, decrypt(vault, ct))
        }
    }

    @Test
    fun `regenerate recovery key invalidates the old one`() {
        val creation = VaultCrypto.createVault(password, testKdf)
        val ct = creation.unlocked.use { encrypt(it, plaintext) }
        val oldKey = creation.recoveryKey

        val (newHeader, newKey) = VaultCrypto.unlockWithPassword(creation.header, password).use { vault ->
            VaultCrypto.regenerateRecoveryKey(creation.header, vault, testKdf)
        }

        assertFailsWith<WrongCredentialsException> { VaultCrypto.unlockWithRecovery(newHeader, oldKey) }
        VaultCrypto.unlockWithRecovery(newHeader, newKey).use { assertContentEquals(plaintext, decrypt(it, ct)) }
        VaultCrypto.unlockWithPassword(newHeader, password).use { assertContentEquals(plaintext, decrypt(it, ct)) }
    }

    @Test
    fun `tampered ciphertext fails authentication`() {
        val creation = VaultCrypto.createVault(password, testKdf)
        creation.unlocked.use { vault ->
            val ct = encrypt(vault, plaintext)
            ct[ct.size / 2] = (ct[ct.size / 2].toInt() xor 0x01).toByte()
            assertFailsWith<Exception> { decrypt(vault, ct) }
        }
    }

    @Test
    fun `header serializes and parses without secrets leaking`() {
        val creation = VaultCrypto.createVault(password, testKdf)
        val text = VaultCrypto.serializeHeader(creation.header)
        // The plaintext recovery key must never appear in the persisted header.
        assertTrue(!text.contains(creation.recoveryKey.replace("-", "")))
        assertEquals(creation.header, VaultCrypto.parseHeader(text))
    }

    @Test
    fun `recovery key encoding round-trips and tolerates formatting`() {
        val secret = randomBytes(32)
        val encoded = RecoveryKey.encode(secret)
        assertContentEquals(secret, RecoveryKey.decode(encoded))
        // Lowercase, ambiguous chars and stray spaces should still decode.
        assertContentEquals(secret, RecoveryKey.decode(" " + encoded.lowercase() + " "))
    }
}
