package dev.vaultmesh.app

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Opt-in "stay unlocked": stashes the vault's key material in the macOS login Keychain so the app can
 * reopen on this device without the master password. macOS-only — on other platforms it reports
 * unsupported and the feature stays hidden. The secret stored is the VMK as hex.
 *
 * Implemented via the system `security` tool to avoid a native-binding dependency. Note: the secret is
 * passed on `security`'s argv when storing, so it is briefly visible to other processes of the SAME
 * user — acceptable given that anyone in your logged-in session can already read the running app's
 * memory. The entry lives in the login keychain (encrypted at rest, unlocked with your macOS login).
 */
class KeychainStore(
    private val service: String = "VaultMesh",
    private val account: String = "default-vault-vmk",
) {
    private val securityBin = File("/usr/bin/security")

    fun isSupported(): Boolean =
        System.getProperty("os.name").lowercase().contains("mac") && securityBin.canExecute()

    /** Stores [secretHex], replacing any existing entry. Returns true on success. */
    fun store(secretHex: String): Boolean = runQuietly(
        listOf(
            securityBin.path, "add-generic-password",
            "-U", // update if it already exists
            "-A", // allow access without a per-read prompt (same-user trust)
            "-s", service, "-a", account,
            "-w", secretHex,
        ),
    ).first

    /** Returns the stored secret hex, or null if absent/unsupported. */
    fun load(): String? {
        if (!isSupported()) return null
        val (ok, out) = runQuietly(
            listOf(securityBin.path, "find-generic-password", "-s", service, "-a", account, "-w"),
        )
        return if (ok) out.trim().ifEmpty { null } else null
    }

    /** Removes the stored secret. Safe to call when none exists. */
    fun clear() {
        if (!isSupported()) return
        runQuietly(listOf(securityBin.path, "delete-generic-password", "-s", service, "-a", account))
    }

    private fun runQuietly(command: List<String>): Pair<Boolean, String> = try {
        val proc = ProcessBuilder(command).redirectErrorStream(false).start()
        val out = proc.inputStream.readBytes().decodeToString()
        val finished = proc.waitFor(10, TimeUnit.SECONDS)
        if (!finished) { proc.destroyForcibly(); false to "" } else (proc.exitValue() == 0) to out
    } catch (_: Exception) {
        false to ""
    }
}
