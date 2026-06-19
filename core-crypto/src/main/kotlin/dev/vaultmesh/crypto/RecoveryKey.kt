package dev.vaultmesh.crypto

/**
 * Encodes the 256-bit recovery secret as Crockford Base32 — a human-friendly, transcription-safe
 * alphabet (no I/L/O/U ambiguity, case-insensitive). Grouped in 4-char blocks for readability.
 *
 * The security comes from the 256 bits of entropy, not the representation; a BIP39 word list can
 * be swapped in later without changing the key material.
 */
object RecoveryKey {
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    fun encode(bytes: ByteArray): String {
        val sb = StringBuilder()
        var buffer = 0
        var bitsLeft = 0
        for (b in bytes) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bitsLeft += 8
            while (bitsLeft >= 5) {
                sb.append(ALPHABET[(buffer shr (bitsLeft - 5)) and 0x1F])
                bitsLeft -= 5
            }
        }
        if (bitsLeft > 0) {
            sb.append(ALPHABET[(buffer shl (5 - bitsLeft)) and 0x1F])
        }
        return sb.toString().chunked(4).joinToString("-")
    }

    fun decode(text: String): ByteArray {
        val clean = text.uppercase()
            .replace("-", "").replace(" ", "")
            .replace('I', '1').replace('L', '1').replace('O', '0')
        var buffer = 0
        var bitsLeft = 0
        val out = ArrayList<Byte>(clean.length * 5 / 8)
        for (c in clean) {
            val idx = ALPHABET.indexOf(c)
            require(idx >= 0) { "Invalid recovery-key character: '$c'" }
            buffer = (buffer shl 5) or idx
            bitsLeft += 5
            if (bitsLeft >= 8) {
                out.add(((buffer shr (bitsLeft - 8)) and 0xFF).toByte())
                bitsLeft -= 8
            }
        }
        return out.toByteArray()
    }
}
