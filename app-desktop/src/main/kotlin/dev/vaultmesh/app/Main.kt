package dev.vaultmesh.app

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Taskbar
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDropEvent
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import javax.imageio.ImageIO

/** Default single-vault location. Multi-vault selection comes in a later phase. */
fun defaultVaultRoot(): Path =
    Paths.get(System.getProperty("user.home"), ".vaultmesh", "default-vault")

fun main() = application {
    val viewModel = remember { AppViewModel(defaultVaultRoot()) }

    // Dock / taskbar icon for the running process (jpackage handles the installed icon separately).
    remember {
        runCatching {
            val img = ImageIO.read(AppViewModel::class.java.getResource("/icon.png"))
            if (Taskbar.isTaskbarSupported()) Taskbar.getTaskbar().iconImage = img
        }
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "VaultMesh",
        icon = painterResource("icon.png"),
        state = rememberWindowState(width = 1100.dp, height = 740.dp),
    ) {
        // Native macOS look: extend content under a transparent title bar and hide the title text,
        // so the window blends with the app instead of showing a plain white bar. No-op elsewhere.
        if (isMac) {
            window.rootPane.putClientProperty("apple.awt.fullWindowContent", true)
            window.rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
            window.rootPane.putClientProperty("apple.awt.windowTitleVisible", false)
        }

        // Accept files dragged from the OS file manager → add them to the current folder.
        DisposableEffect(Unit) {
            val listener = object : DropTargetAdapter() {
                override fun drop(event: DropTargetDropEvent) {
                    runCatching {
                        event.acceptDrop(DnDConstants.ACTION_COPY)
                        val t = event.transferable
                        if (t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                            @Suppress("UNCHECKED_CAST")
                            val files = t.getTransferData(DataFlavor.javaFileListFlavor) as List<File>
                            val paths = files.map { it.toPath() }
                            if (paths.isNotEmpty() && viewModel.screen == Screen.Files) viewModel.addPaths(paths)
                        }
                        event.dropComplete(true)
                    }.onFailure { event.dropComplete(false) }
                }
            }
            window.dropTarget = DropTarget(window, DnDConstants.ACTION_COPY, listener, true)
            onDispose { window.dropTarget = null }
        }

        VaultMeshTheme {
            AppRoot(viewModel)
        }
    }

    // A separate, resizable window for browsing a connected storage's actual contents + capacity.
    viewModel.explorerTarget?.let { target ->
        Window(
            onCloseRequest = viewModel::closeExplorer,
            title = "${target.displayName} — Storage",
            icon = painterResource("icon.png"),
            state = rememberWindowState(width = 920.dp, height = 660.dp),
        ) {
            // Plain native title bar here (it shows the storage name); no content-under-titlebar overlay.
            VaultMeshTheme {
                StorageExplorerWindow(viewModel, target)
            }
        }
    }
}
