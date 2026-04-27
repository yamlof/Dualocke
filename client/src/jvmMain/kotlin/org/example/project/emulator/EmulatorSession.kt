package org.example.project.emulator

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.example.project.LibretroCore
import org.example.project.NativeLoader
import java.awt.image.BufferedImage

class EmulatorSession {
    private var core: LibretroCore? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var image: BufferedImage? = null

    private val _frame = MutableStateFlow<ImageBitmap?>(null)
    val frame: StateFlow<ImageBitmap?> = _frame

    fun start(romPath: String): Boolean {
        if (core != null) return true
        val c = LibretroCore()
        val coreFile = NativeLoader.extractCore("mgba")
        if (!c.loadCore(coreFile)) return false
        if (!c.loadGame(romPath)) {
            c.unloadCore()
            return false
        }
        core = c
        image = BufferedImage(c.frameWidth, c.frameHeight, BufferedImage.TYPE_INT_ARGB)
        scope.launch {
            val img = image!!
            val w = c.frameWidth
            val h = c.frameHeight
            while (isActive) {
                val pixels = c.runFrame()
                img.setRGB(0, 0, w, h, pixels, 0, w)
                _frame.value = img.toComposeImageBitmap()
                delay(16)
            }
        }
        return true
    }

    fun dispose() {
        scope.cancel()
        core?.unloadGame()
        core?.unloadCore()
        core = null
        _frame.value = null
    }
}