package org.example.project

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Kotlin wrapper around the libretro JNI bridge.
 *
 * Usage:
 *   val core = LibretroCore()
 *   core.loadCore("/path/to/mgba_libretro.dylib")
 *   core.loadGame("/path/to/firered.gba")
 *   while (running) {
 *       val frame = core.runFrame()
 *       renderFrame(frame, core.frameWidth, core.frameHeight)
 *   }
 *   core.unloadGame()
 *   core.unloadCore()
 */
class LibretroCore {

    // --- Native methods ---
    private external fun nativeLoadCore(corePath: String): Boolean
    private external fun nativeLoadGame(romPath: String): Boolean
    private external fun nativeRunFrame(outBuffer: IntArray)
    private external fun nativeGetFrameWidth(): Int
    private external fun nativeGetFrameHeight(): Int
    private external fun nativeSetInput(state: Int)
    private external fun nativeReadByte(memoryId: Int, offset: Int): Int
    private external fun nativeWriteByte(memoryId: Int, offset: Int, value: Int)
    private external fun nativeReset()
    private external fun nativeUnloadGame()
    private external fun nativeUnloadCore()

    private external fun nativeGetSaveRam(): ByteArray?
    private external fun nativeSetSaveRam(data: ByteArray): Boolean

    // --- Public state ---
    val frameWidth: Int  get() = nativeGetFrameWidth()
    val frameHeight: Int get() = nativeGetFrameHeight()

    // Reusable buffer to avoid per-frame allocations
    private var frameBuffer: IntArray = IntArray(240 * 160)

    // --- Lifecycle ---

    fun loadCore(corePath: String): Boolean = nativeLoadCore(corePath)

    fun loadGame(romPath: String): Boolean {
        val ok = nativeLoadGame(romPath)
        if (ok) {
            // Resize buffer to match the loaded game's resolution
            val w = frameWidth.coerceAtLeast(1)
            val h = frameHeight.coerceAtLeast(1)
            frameBuffer = IntArray(w * h)
        }
        return ok
    }

    /** Run one emulated frame and return the rendered ARGB8888 pixel buffer. */
    fun runFrame(): IntArray {
        nativeRunFrame(frameBuffer)
        return frameBuffer
    }

    fun reset() = nativeReset()
    fun unloadGame() = nativeUnloadGame()
    fun unloadCore() = nativeUnloadCore()

    // --- Input ---

    fun setInput(buttons: Set<RetroPadButton>) {
        val mask = buttons.fold(0) { acc, b -> acc or (1 shl b.id) }
        nativeSetInput(mask)
    }

    fun setInput(buttonsMask: Int) = nativeSetInput(buttonsMask)

    // --- Memory access ---

    /**
     * Read a byte from the emulated system's main RAM.
     * For GBA, EWRAM (the area Pokémon games mostly use) lives at game-space 0x02000000+.
     * The libretro buffer for SYSTEM_RAM is flat — pass `address - 0x02000000` as the offset.
     */
    fun readByte(offset: Int): Int =
        nativeReadByte(MemoryId.SYSTEM_RAM.id, offset)

    fun readByte(memory: MemoryId, offset: Int): Int =
        nativeReadByte(memory.id, offset)

    fun writeByte(offset: Int, value: Int) =
        nativeWriteByte(MemoryId.SYSTEM_RAM.id, offset, value)

    fun writeByte(memory: MemoryId, offset: Int, value: Int) =
        nativeWriteByte(memory.id, offset, value)

    /** Read 4 bytes (little-endian) starting at offset. */
    fun readInt(offset: Int): Int {
        val b0 = readByte(offset) and 0xFF
        val b1 = readByte(offset + 1) and 0xFF
        val b2 = readByte(offset + 2) and 0xFF
        val b3 = readByte(offset + 3) and 0xFF
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }

    /** Read 2 bytes (little-endian) starting at offset. */
    fun readShort(offset: Int): Int {
        val b0 = readByte(offset) and 0xFF
        val b1 = readByte(offset + 1) and 0xFF
        return b0 or (b1 shl 8)
    }

    fun getSaveRam(): ByteArray? = nativeGetSaveRam()

    fun setSaveRam(data: ByteArray): Boolean = nativeSetSaveRam(data)

    companion object {
        init {
            NativeLoader.loadEmulatorCore()
        }
    }
}

// ============================================================
// Memory regions exposed by libretro
// ============================================================

enum class MemoryId(val id: Int) {
    SAVE_RAM(0),
    RTC(1),
    SYSTEM_RAM(2),
    VIDEO_RAM(3);
}

// ============================================================
// RetroPad button mapping (matches RETRO_DEVICE_ID_JOYPAD_*)
// ============================================================

enum class RetroPadButton(val id: Int) {
    B(0),
    Y(1),
    SELECT(2),
    START(3),
    UP(4),
    DOWN(5),
    LEFT(6),
    RIGHT(7),
    A(8),
    X(9),
    L(10),
    R(11),
    L2(12),
    R2(13),
    L3(14),
    R3(15);
}

// ============================================================
// Platform detection
// ============================================================

object Platform {
    enum class OS { MACOS, LINUX, WINDOWS }
    enum class Arch { X64, ARM64 }

    val os: OS by lazy {
        val name = System.getProperty("os.name").lowercase()
        when {
            name.contains("mac") || name.contains("darwin") -> OS.MACOS
            name.contains("win") -> OS.WINDOWS
            else -> OS.LINUX
        }
    }

    val arch: Arch by lazy {
        val a = System.getProperty("os.arch").lowercase()
        if (a.contains("aarch64") || a.contains("arm64")) Arch.ARM64 else Arch.X64
    }

    /** Subdirectory inside resources/ for this platform's binaries. */
    val resourceFolder: String by lazy {
        when (os) {
            OS.MACOS   -> if (arch == Arch.ARM64) "macos-arm64" else "macos-x64"
            OS.LINUX   -> if (arch == Arch.ARM64) "linux-arm64" else "linux-x64"
            OS.WINDOWS -> "windows-x64"
        }
    }

    val libExtension: String by lazy {
        when (os) {
            OS.MACOS -> "dylib"
            OS.LINUX -> "so"
            OS.WINDOWS -> "dll"
        }
    }

    /** "lib" prefix for Unix shared libs, empty on Windows. */
    val libPrefix: String by lazy { if (os == OS.WINDOWS) "" else "lib" }
}

// ============================================================
// Native library loader (extracts to temp and loads)
// ============================================================

object NativeLoader {

    private val cacheDir: Path = Paths.get(
        System.getProperty("java.io.tmpdir"), "dualocke-native"
    ).also { Files.createDirectories(it) }

    /** Load the JNI bridge (libemulator_core / emulator_core.dll). */
    fun loadEmulatorCore() {
        loadFromResources("emulator_core")
    }

    /** Extract a libretro core from resources/cores/<platform>/ to disk and return its path. */
    fun extractCore(coreName: String): String {
        val fileName = "${coreName}_libretro.${Platform.libExtension}"
        val resourcePath = "/cores/${Platform.resourceFolder}/$fileName"
        val target = cacheDir.resolve(fileName).toFile()

        if (target.exists()) target.delete()   // ← changed: always re-extract

        val stream = NativeLoader::class.java.getResourceAsStream(resourcePath)
            ?: error("Core not found in resources: $resourcePath")
        stream.use { input ->
            target.outputStream().use { input.copyTo(it) }
        }
        if (Platform.os != Platform.OS.WINDOWS) {
            target.setExecutable(true)
        }
        return target.absolutePath
    }

    private fun loadFromResources(libName: String) {
        val fileName = "${Platform.libPrefix}$libName.${Platform.libExtension}"
        val resourcePath = "/native/${Platform.resourceFolder}/$fileName"

        val target = cacheDir.resolve(fileName).toFile()
        if (target.exists()) target.delete()   // ← always re-extract

        val stream = NativeLoader::class.java.getResourceAsStream(resourcePath)
            ?: error("Native lib not found: $resourcePath")
        stream.use { input ->
            target.outputStream().use { input.copyTo(it) }
        }
        System.load(target.absolutePath)
    }
}