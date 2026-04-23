package org.example.project

import org.example.project.data.FilePathProvider
import java.io.File
import java.lang.System.getProperty


fun launchMGBA(): Process {
    val os = getProperty("os.name").lowercase()
    val isMac = os.contains("mac") || os.contains("darwin")
    val isLinux = os.contains("linux")

    val mgbaDir = if (isMac) File("resources/mac") else File("resources/linux")
    val luaScript = File("resources").resolve("nuzlocke.lua")

    val mgbaBinary = when {
        isMac -> File("resources/mac/mGBA.app/Contents/MacOS/mGBA")
        isLinux -> File("resources/linux/mGBAtrueone.appimage")
        else -> throw UnsupportedOperationException("Unsupported OS: $os")
    }

    val rom = FilePathProvider.getActiveRomFile()
        ?: throw IllegalStateException("No ROM loaded. Import a ROM first.")


    require(mgbaBinary.exists()) { "mGBA binary not found: ${mgbaBinary.absolutePath}" }
    require(luaScript.exists()) { "Lua script not found: ${luaScript.absolutePath}" }

    mgbaBinary.setExecutable(true)

    val pb = ProcessBuilder(
        mgbaBinary.absolutePath,
        "--script", luaScript.absolutePath,
        rom.absolutePath
    )
    pb.directory(mgbaDir)
    pb.redirectErrorStream(true)

    return pb.start()
}
