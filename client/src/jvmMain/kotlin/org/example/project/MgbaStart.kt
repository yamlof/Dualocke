package org.example.project

import java.io.File
import java.lang.System.getProperty


fun launchMGBA(): Process {
    val os = getProperty("os.name").lowercase()
    val isMac = os.contains("mac") || os.contains("darwin")
    val isLinux = os.contains("linux")

    val baseDir = File("resources")
    val mgbaDir = if (isMac) File("resources/mac") else File("resources/linux")
    val romsDir = File("resources")
    val scriptDir = File("resources")

    val mgbaBinary = when {
        isMac -> File("resources/mac/mGBA.app/Contents/MacOS/mGBA")
        isLinux -> File("resources/linux/mGBAtrueone.appimage")
        else -> throw UnsupportedOperationException("unsupported OS: ${os}")
    }
    val rom = romsDir.resolve("roms/firered.gba")
    val luaScript = scriptDir.resolve("nuzlocke.lua")

    require(mgbaBinary.exists()) { "mGBA binary not found: ${mgbaBinary.absolutePath}" }
    require(rom.exists()) { "ROM not found: ${rom.absolutePath}" }
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