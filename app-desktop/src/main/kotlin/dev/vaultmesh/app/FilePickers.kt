package dev.vaultmesh.app

import java.io.File
import java.nio.file.Path
import javax.swing.JFileChooser

/** Thin wrappers over Swing file dialogs (native enough, and reliable on all three desktops). */
object FilePickers {

    fun pickFilesOrFolders(): List<Path> {
        val chooser = JFileChooser().apply {
            dialogTitle = "Add files or folders to the vault"
            fileSelectionMode = JFileChooser.FILES_AND_DIRECTORIES
            isMultiSelectionEnabled = true
        }
        return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFiles.map { it.toPath() }
        } else {
            emptyList()
        }
    }

    fun pickDirectory(): Path? {
        val chooser = JFileChooser().apply {
            dialogTitle = "Choose a folder to mirror the encrypted vault into"
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        }
        return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile.toPath()
        } else {
            null
        }
    }

    fun pickSaveDestination(suggestedName: String): Path? {
        val chooser = JFileChooser().apply {
            dialogTitle = "Export decrypted file to…"
            selectedFile = File(suggestedName)
        }
        return if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile.toPath()
        } else {
            null
        }
    }
}
