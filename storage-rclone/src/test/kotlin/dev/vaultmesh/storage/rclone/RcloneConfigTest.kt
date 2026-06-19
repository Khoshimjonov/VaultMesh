package dev.vaultmesh.storage.rclone

import dev.vaultmesh.crypto.KdfParams
import dev.vaultmesh.storage.StorageTarget
import dev.vaultmesh.storage.TargetKind
import dev.vaultmesh.vault.VaultFactory
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeBytes
import kotlin.test.assertTrue

/**
 * Verifies the provider-connect mechanism that the in-app OAuth/credential flow uses:
 * createRemote() registers a remote, it shows up in listConfiguredRemotes(), and it can then be
 * used as a sync target via its "<remote>:<path>" fs root. Uses a no-OAuth `alias` remote so it
 * needs no credentials or browser — the OAuth path is identical except rclone also runs the
 * browser token exchange.
 */
class RcloneConfigTest {

    @Test
    fun `createRemote registers a usable remote`(@TempDir tmp: Path) = runBlocking {
        assumeTrue(RcloneBinary.isAvailable(), "rclone binary not available; skipping")

        val aliasTarget = tmp.resolve("aliasedStorage").also { it.createDirectories() }
        val vaultDir = tmp.resolve("vault")
        val srcDir = tmp.resolve("src").also { it.createDirectories() }
        srcDir.resolve("f.bin").writeBytes("payload".toByteArray())

        VaultFactory.create(vaultDir, "p".toCharArray(), { KdfParams.forTesting(it) }).vault
            .use { it.addFolder(srcDir, "docs") }

        RcloneDaemon.start(configPath = tmp.resolve("rclone.conf")).use { daemon ->
            val engine = RcloneStorageEngine(daemon.rc)

            // Same call the credential/OAuth connect uses (here: a no-auth alias backend).
            engine.createRemote("mybackup", "alias", mapOf("remote" to aliasTarget.toString()))
            assertTrue("mybackup" in engine.listConfiguredRemotes(), "remote must be registered")

            // Use the named remote as a target: pushes into the aliased directory.
            val target = StorageTarget(UUID.randomUUID().toString(), "mybackup", TargetKind.RCLONE_REMOTE, "mybackup:VaultMesh")
            val result = engine.push(vaultDir, target)
            assertTrue(result.ok, "push via named remote failed: ${result.error}")

            assertTrue(aliasTarget.resolve("VaultMesh").resolve("vault.json").exists())
            val objects = Files.list(aliasTarget.resolve("VaultMesh").resolve("objects")).use { it.count() }
            assertTrue(objects > 0)
        }
    }
}
