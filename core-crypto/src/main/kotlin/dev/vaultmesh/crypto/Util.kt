package dev.vaultmesh.crypto

import java.security.SecureRandom
import java.util.Base64

/** Base64 (no line breaks) helpers used across the crypto layer. */
object B64 {
    fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
    fun decode(s: String): ByteArray = Base64.getDecoder().decode(s)
}

internal val secureRandom = SecureRandom()

internal fun randomBytes(n: Int): ByteArray = ByteArray(n).also { secureRandom.nextBytes(it) }

/** Overwrite sensitive bytes in place so they don't linger in the heap after use. */
fun ByteArray.wipe() {
    java.util.Arrays.fill(this, 0)
}
