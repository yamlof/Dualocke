package org.example.project.emulator

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.example.project.LibretroCore
import org.example.project.NativeLoader
import org.example.project.RetroPadButton
import java.awt.image.BufferedImage


/**
 * Session-level wrapper around [LibretroCore].
 *
 * Adds since the last revision:
 *   - Fast-forward: when [speedMultiplier] is N, the loop runs runFrame() N
 *     times per tick before rendering. Skipped frames are not rendered.
 */
class EmulatorSession(
    private val coreName: String = "mgba",
    /** The multiplier used when fast-forward is toggled on. */
    private val fastForwardMultiplier: Int = 4,
) {

    enum class State { IDLE, READY, RUNNING, STOPPED }

    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _frame = MutableStateFlow<ImageBitmap?>(null)
    val frame: StateFlow<ImageBitmap?> = _frame.asStateFlow()

    private val _heldButtons = MutableStateFlow<Set<RetroPadButton>>(emptySet())
    val heldButtons: StateFlow<Set<RetroPadButton>> = _heldButtons.asStateFlow()

    /** 1 = real-time, [fastForwardMultiplier] = fast-forward. */
    private val _speedMultiplier = MutableStateFlow(1)
    val speedMultiplier: StateFlow<Int> = _speedMultiplier.asStateFlow()

    var core: LibretroCore? = null
        private set

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var loopJob: Job? = null
    private var bufferedImage: BufferedImage? = null

    fun start(romPath: String): Boolean {
        if (_state.value != State.IDLE) {
            println("[EmulatorSession] start() called while in state ${_state.value}; ignoring")
            return false
        }

        val newCore = LibretroCore()
        val corePath = NativeLoader.extractCore(coreName)

        if (!newCore.loadCore(corePath)) {
            println("[EmulatorSession] Failed to load core: $corePath")
            _state.value = State.IDLE
            return false
        }
        if (!newCore.loadGame(romPath)) {
            println("[EmulatorSession] Failed to load game: $romPath")
            newCore.unloadCore()
            _state.value = State.IDLE
            return false
        }

        core = newCore
        bufferedImage = BufferedImage(
            newCore.frameWidth.coerceAtLeast(1),
            newCore.frameHeight.coerceAtLeast(1),
            BufferedImage.TYPE_INT_ARGB,
        )
        _state.value = State.READY
        startLoop()
        return true
    }

    private fun startLoop() {
        val activeCore = core ?: return
        val img = bufferedImage ?: return
        val width = activeCore.frameWidth
        val height = activeCore.frameHeight

        loopJob?.cancel()
        loopJob = scope.launch {
            _state.value = State.RUNNING
            try {
                while (isActive) {
                    val multiplier = _speedMultiplier.value.coerceAtLeast(1)
                    val held = _heldButtons.value

                    // Run N frames; render only the last to keep input alive
                    // and avoid burning GPU on intermediate bitmaps.
                    repeat(multiplier - 1) {
                        activeCore.setInput(held)
                        activeCore.runFrame()
                    }

                    activeCore.setInput(held)
                    val pixels = activeCore.runFrame()
                    img.setRGB(0, 0, width, height, pixels, 0, width)
                    _frame.value = img.toComposeImageBitmap()

                    delay(16)
                }
            } catch (t: Throwable) {
                println("[EmulatorSession] Frame loop error: ${t.message}")
                t.printStackTrace()
            }
        }
    }

    // --- Input ---

    fun pressButton(button: RetroPadButton) {
        _heldButtons.update { it + button }
    }

    fun releaseButton(button: RetroPadButton) {
        _heldButtons.update { it - button }
    }

    fun clearAllInput() {
        _heldButtons.value = emptySet()
    }

    // --- Speed control ---

    /** Toggle between 1x and the fast-forward multiplier. */
    fun toggleFastForward() {
        _speedMultiplier.value = if (_speedMultiplier.value == 1) fastForwardMultiplier else 1
    }

    /** Force a specific speed (1 = normal, anything > 1 = fast-forward). */
    fun setSpeedMultiplier(multiplier: Int) {
        _speedMultiplier.value = multiplier.coerceAtLeast(1)
    }

    // --- Lifecycle ---

    fun reset() {
        core?.reset()
    }

    fun stop() {
        loopJob?.cancel()
        loopJob = null
        clearAllInput()
        _speedMultiplier.value = 1
        core?.let {
            try {
                it.unloadGame()
            } catch (t: Throwable) {
                println("[EmulatorSession] unloadGame failed: ${t.message}")
            }
            try {
                it.unloadCore()
            } catch (t: Throwable) {
                println("[EmulatorSession] unloadCore failed: ${t.message}")
            }
        }
        core = null
        bufferedImage = null
        _frame.value = null
        _state.value = State.IDLE
    }

    fun dispose() {
        stop()
        scope.cancel()
    }
}

private inline fun <T> MutableStateFlow<T>.update(transform: (T) -> T) {
    while (true) {
        val current = value
        val next = transform(current)
        if (compareAndSet(current, next)) return
    }
}