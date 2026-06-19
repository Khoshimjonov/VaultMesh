package dev.vaultmesh.app

import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.nio.file.Path
import java.nio.file.Paths

/** Default single-vault location. Multi-vault selection comes in a later phase. */
fun defaultVaultRoot(): Path =
    Paths.get(System.getProperty("user.home"), ".vaultmesh", "default-vault")

fun main() = application {
    val viewModel = remember { AppViewModel(defaultVaultRoot()) }
    Window(
        onCloseRequest = ::exitApplication,
        title = "VaultMesh",
        state = rememberWindowState(width = 1000.dp, height = 700.dp),
    ) {
        VaultMeshTheme {
            AppRoot(viewModel)
        }
    }
}
