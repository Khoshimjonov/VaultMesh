package dev.vaultmesh.crypto

import com.google.crypto.tink.subtle.AesGcmHkdfStreaming
import java.io.InputStream
import java.io.OutputStream

/**
 * Streaming authenticated encryption for file/chunk content using AES-256-GCM-HKDF-STREAMING.
 *
 * Splits the stream into authenticated segments, so:
 *  - encryption/decryption run in constant memory regardless of file size,
 *  - tampering with any segment is detected on read,
 *  - decryption can resume / seek at segment boundaries (useful for large remote files).
 */
class ContentCipher(contentKey: ByteArray) {

    private val keySizeBytes = 32
    private val segmentSize = 1 shl 20 // 1 MiB ciphertext segments
    private val streaming = AesGcmHkdfStreaming(
        contentKey,
        "HmacSha256",
        keySizeBytes,
        segmentSize,
        /* firstSegmentOffset = */ 0,
    )

    /**
     * Wraps [out] so everything written is encrypted. [associatedData] is authenticated but not
     * encrypted (use it to bind ciphertext to its logical identity, e.g. a chunk id). The caller
     * must close the returned stream to flush the final segment.
     */
    fun encryptingStream(out: OutputStream, associatedData: ByteArray): OutputStream =
        streaming.newEncryptingStream(out, associatedData)

    /** Wraps [input] so everything read is decrypted and authenticated against [associatedData]. */
    fun decryptingStream(input: InputStream, associatedData: ByteArray): InputStream =
        streaming.newDecryptingStream(input, associatedData)
}
