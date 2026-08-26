package org.example.project.utils

import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

object FilePicker {

    fun pickRomFile(onFileSelected: (File) -> Unit) {
        val chooser = JFileChooser()
        chooser.dialogTitle = "Select Pokemon ROM"
        chooser.fileSelectionMode = JFileChooser.FILES_ONLY

        chooser.fileFilter = FileNameExtensionFilter(
            "Game Boy ROMs (*.gba, *.gb, *.gbc)",
            "gba", "gb", "gbc"
        )

        val result = chooser.showOpenDialog(null)
        if (result == JFileChooser.APPROVE_OPTION) {
            onFileSelected(chooser.selectedFile)
        }
    }
}
