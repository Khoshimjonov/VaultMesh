package dev.vaultmesh.vault

import dev.vaultmesh.crypto.KdfParams
import dev.vaultmesh.crypto.VaultCrypto
import dev.vaultmesh.crypto.VaultHeader
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/** A freshly created vault plus the one-time recovery key to show the user. */
data class NewVault(val vault: Vault, val recoveryKey: String)

/**
 * Creates and opens on-disk vaults. The header (`vault.json`) is the only file written in the
 * clear, and it contains no usable secrets.
 */
object VaultFactory {

    private fun headerFile(root: Path): Path = root.resolve("vault.json")

    fun exists(root: Path): Boolean = headerFile(root).exists()

    fun readHeader(root: Path): VaultHeader =
        VaultCrypto.parseHeader(headerFile(root).readText())

    fun create(
        root: Path,
        password: CharArray,
        kdfFactory: (ByteArray) -> KdfParams = { KdfParams.production(it) },
        deviceId: String = "local",
    ): NewVault {
        require(!exists(root)) { "A vault already exists at $root" }
        Files.createDirectories(root)
        val creation = VaultCrypto.createVault(password, kdfFactory)
        writeHeader(root, creation.header)
        return NewVault(Vault(root, creation.header, creation.unlocked, deviceId), creation.recoveryKey)
    }

    fun unlockWithPassword(root: Path, password: CharArray, deviceId: String = "local"): Vault {
        val header = readHeader(root)
        return Vault(root, header, VaultCrypto.unlockWithPassword(header, password), deviceId)
    }

    fun unlockWithRecovery(root: Path, recoveryKey: String, deviceId: String = "local"): Vault {
        val header = readHeader(root)
        return Vault(root, header, VaultCrypto.unlockWithRecovery(header, recoveryKey), deviceId)
    }

    /** Persists an updated header (e.g. after a password change). */
    fun writeHeader(root: Path, header: VaultHeader) {
        headerFile(root).writeText(VaultCrypto.serializeHeader(header))
    }
}
