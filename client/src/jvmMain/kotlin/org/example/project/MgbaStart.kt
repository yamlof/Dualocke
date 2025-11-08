package org.example.project

import java.io.File
import java.lang.System.getProperty


fun lauchMGBA(): Process{
    val os = getProperty("os.name").lowercase()
    val mgbaDir = File("resources/mgba")
    val romsDir = File("resources")

    val mgbaBinary = when {
        os.contains("mac") -> mgbaDir.resolve("mac/mGBA.app/Contents/MacOS/mGBA")
        else -> mgbaDir.resolve("linux/mgba")

    }

    val rom = romsDir.resolve("roms/Pokemon - FireRed Version (USA, Europe) (Rev 1).gba")

    val luaScript = mgbaDir.resolve("nuzlocke.lua")


    val pb = ProcessBuilder(
        mgbaBinary.absolutePath,
        "--script", luaScript.absolutePath,
        rom.absolutePath
    ).directory(mgbaDir)
    return pb.start()
}