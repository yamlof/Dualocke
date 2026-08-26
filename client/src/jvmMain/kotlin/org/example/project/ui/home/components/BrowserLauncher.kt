package org.example.project.ui.home.components


import java.awt.Desktop
import java.net.URI

object BrowserLauncher {
    fun openUrl(url: String): Boolean {
        return try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI(url))
                true
            } else {
                val os = System.getProperty("os.name").lowercase()
                val command = when {
                    os.contains("mac") -> arrayOf("open", url)
                    os.contains("win") -> arrayOf("rundll32", "url.dll,FileProtocolHandler", url)
                    else -> arrayOf("xdg-open", url)
                }
                Runtime.getRuntime().exec(command)
                true
            }
        } catch (e: Exception) {
            println("Failed to open browser: ${e.message}")
            false
        }
    }
}