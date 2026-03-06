package org.example.project.utils

import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

object FilePicker {

    /**
     * Pick a Pokemon ROM file (.gba, .gb, .gbc)
     */
    fun pickRomFile(onFileSelected: (File) -> Unit) {
        val chooser = JFileChooser()
        chooser.dialogTitle = "Select Pokemon ROM"
        chooser.fileSelectionMode = JFileChooser.FILES_ONLY

        // Add file filter for ROM files
        chooser.fileFilter = FileNameExtensionFilter(
            "Game Boy ROMs (*.gba, *.gb, *.gbc)",
            "gba", "gb", "gbc"
        )

        val result = chooser.showOpenDialog(null)
        if (result == JFileChooser.APPROVE_OPTION) {
            onFileSelected(chooser.selectedFile)
        }
    }

    /**
     * Pick a save file (.sav)
     */
    fun pickSaveFile(onFileSelected: (File) -> Unit) {
        val chooser = JFileChooser()
        chooser.dialogTitle = "Select Save File"
        chooser.fileSelectionMode = JFileChooser.FILES_ONLY

        chooser.fileFilter = FileNameExtensionFilter(
            "Save Files (*.sav)",
            "sav"
        )

        val result = chooser.showOpenDialog(null)
        if (result == JFileChooser.APPROVE_OPTION) {
            onFileSelected(chooser.selectedFile)
        }
    }

    /**
     * Pick multiple ROM files at once
     */
    fun pickMultipleRoms(onFilesSelected: (List<File>) -> Unit) {
        val chooser = JFileChooser()
        chooser.dialogTitle = "Select Pokemon ROMs"
        chooser.isMultiSelectionEnabled = true
        chooser.fileSelectionMode = JFileChooser.FILES_ONLY

        chooser.fileFilter = FileNameExtensionFilter(
            "Game Boy ROMs (*.gba, *.gb, *.gbc)",
            "gba", "gb", "gbc"
        )

        val result = chooser.showOpenDialog(null)
        if (result == JFileChooser.APPROVE_OPTION) {
            onFilesSelected(chooser.selectedFiles.toList())
        }
    }

    /**
     * Pick a directory (for batch import)
     */
    fun pickDirectory(onDirectorySelected: (File) -> Unit) {
        val chooser = JFileChooser()
        chooser.dialogTitle = "Select Directory"
        chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY

        val result = chooser.showOpenDialog(null)
        if (result == JFileChooser.APPROVE_OPTION) {
            onDirectorySelected(chooser.selectedFile)
        }
    }

    /**
     * Save file dialog (for exporting)
     */
    fun saveFile(
        defaultName: String,
        extension: String,
        onLocationSelected: (File) -> Unit
    ) {
        val chooser = JFileChooser()
        chooser.dialogTitle = "Save File"
        chooser.selectedFile = File(defaultName)

        chooser.fileFilter = FileNameExtensionFilter(
            "${extension.uppercase()} Files (*.${extension})",
            extension
        )

        val result = chooser.showSaveDialog(null)
        if (result == JFileChooser.APPROVE_OPTION) {
            var file = chooser.selectedFile

            // Add extension if not present
            if (!file.name.endsWith(".$extension")) {
                file = File(file.parent, "${file.name}.$extension")
            }

            onLocationSelected(file)
        }
    }
}
