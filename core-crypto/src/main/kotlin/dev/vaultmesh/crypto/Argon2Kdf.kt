package dev.vaultmesh.crypto

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters

/**
 * Argon2id key derivation. Turns a low-entropy secret (master password or recovery key)
 * plus a stored salt into a high-entropy key-encryption-key (KEK/RKEK).
 *
 * Argon2id is memory-hard, which is what makes brute-forcing the master password expensive
 * even with GPUs/ASICs.
 */
object Argon2Kdf {

    /** Derive a key of [KdfParams.keyLenBytes] bytes from [secret] using [params]. */
    fun deriveKey(secret: ByteArray, params: KdfParams): ByteArray {
        require(params.algorithm == "argon2id") { "Unsupported KDF: ${params.algorithm}" }
        val parameters = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withIterations(params.iterations)
            .withMemoryAsKB(params.memoryKib)
            .withParallelism(params.parallelism)
            .withSalt(B64.decode(params.saltB64))
            .build()
        val generator = Argon2BytesGenerator().apply { init(parameters) }
        val out = ByteArray(params.keyLenBytes)
        generator.generateBytes(secret, out)
        return out
    }

    /** Convenience: derive from a char array password (UTF-8), wiping the intermediate bytes. */
    fun deriveKey(password: CharArray, params: KdfParams): ByteArray {
        val bytes = charsToUtf8(password)
        try {
            return deriveKey(bytes, params)
        } finally {
            bytes.wipe()
        }
    }

    private fun charsToUtf8(chars: CharArray): ByteArray {
        val cb = java.nio.CharBuffer.wrap(chars)
        val bb = Charsets.UTF_8.newEncoder().encode(cb)
        val out = ByteArray(bb.remaining())
        bb.get(out)
        return out
    }
}
