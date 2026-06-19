package dev.vaultmesh.crypto

import com.google.crypto.tink.subtle.AesGcmJce
import com.google.crypto.tink.subtle.Hkdf
import java.security.GeneralSecurityException

/** Wrong master password / recovery key (AEAD authentication failed on unwrap). */
class WrongCredentialsException(message: String = "Invalid password or recovery key") :
    GeneralSecurityException(message)

/**
 * Envelope key wrapping. The Vault Master Key (VMK) is encrypted under a key-encryption-key
 * (KEK) derived from the password (and, independently, under one derived from the recovery key).
 * AES-256-GCM authenticates the wrap, so an incorrect KEK fails cleanly rather than producing
 * garbage.
 */
object KeyWrap {
    fun wrap(kek: ByteArray, plaintext: ByteArray, aad: ByteArray): WrappedKey {
        val aead = AesGcmJce(kek)
        return WrappedKey(B64.encode(aead.encrypt(plaintext, aad)))
    }

    fun unwrap(kek: ByteArray, wrapped: WrappedKey, aad: ByteArray): ByteArray {
        val aead = AesGcmJce(kek)
        return try {
            aead.decrypt(B64.decode(wrapped.ciphertextB64), aad)
        } catch (e: GeneralSecurityException) {
            throw WrongCredentialsException()
        }
    }
}

/** Purpose-separated subkey derivation from the VMK via HKDF-SHA256. */
object SubKeys {
    const val CONTENT = "vaultmesh:content:v1"
    const val FILENAME = "vaultmesh:filename:v1"
    const val MANIFEST = "vaultmesh:manifest:v1"
    const val REF_MAC = "vaultmesh:ref-mac:v1"

    fun derive(vmk: ByteArray, info: String, lengthBytes: Int = 32): ByteArray =
        Hkdf.computeHkdf("HMACSHA256", vmk, ByteArray(0), info.toByteArray(Charsets.UTF_8), lengthBytes)
}
