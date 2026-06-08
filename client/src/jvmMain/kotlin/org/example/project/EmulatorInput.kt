package org.example.project

import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import org.example.project.RetroPadButton
import org.example.project.emulator.EmulatorSession


/**
 * Wraps an emulator display with keyboard input handling.
 *
 * Behaviour:
 *   - Tapping the wrapped content requests keyboard focus.
 *   - While focused, mapped keys are translated into RetroPad button
 *     press/release events on the [session].
 *   - Shift+Tab (KeyDown only) toggles fast-forward.
 *   - Cmd/Ctrl/Alt-modified keys are NOT consumed — Cmd+Q etc. still work.
 *     (Shift is allowed because we use Shift+Tab as the FF hotkey.)
 *   - On focus loss, all held buttons are released.
 *   - When the emulator transitions to RUNNING, focus is requested (best-effort).
 */
@Composable
fun EmulatorInput(
    session: EmulatorSession,
    modifier: Modifier = Modifier,
    keymap: Map<Key, RetroPadButton> = DefaultKeymap,
    content: @Composable () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }

    val state by session.state.collectAsState()

    LaunchedEffect(state) {
        if (state == EmulatorSession.State.RUNNING) {
            try {
                focusRequester.requestFocus()
            } catch (_: Throwable) {
                // FocusRequester not yet attached — user can click to focus.
            }
        }
    }

    Box(
        modifier = modifier
            .focusRequester(focusRequester)
            .focusable()
            .onFocusChanged { focusState ->
                val nowFocused = focusState.isFocused
                if (isFocused && !nowFocused) {
                    session.clearAllInput()
                }
                isFocused = nowFocused
            }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { focusRequester.requestFocus() })
            }
            .onKeyEvent { event ->
                // --- Hotkey: Shift+Tab toggles fast-forward (KeyDown only) ---
                if (event.key == Key.Tab && event.isShiftPressed && event.type == KeyEventType.KeyDown) {
                    session.toggleFastForward()
                    return@onKeyEvent true   // consume so focus traversal doesn't fire
                }
                // We also need to consume the matching KeyUp so it doesn't
                // leak into focus traversal on release.
                if (event.key == Key.Tab && event.isShiftPressed && event.type == KeyEventType.KeyUp) {
                    return@onKeyEvent true
                }

                // --- Block other modifiers from triggering RetroPad buttons ---
                // Cmd+Q, Ctrl+W etc. must pass through to the OS / app.
                if (event.isMetaPressed || event.isCtrlPressed || event.isAltPressed) {
                    return@onKeyEvent false
                }

                // --- RetroPad button mapping ---
                val button = keymap[event.key] ?: return@onKeyEvent false
                when (event.type) {
                    KeyEventType.KeyDown -> {
                        session.pressButton(button)
                        true
                    }
                    KeyEventType.KeyUp -> {
                        session.releaseButton(button)
                        true
                    }
                    else -> false
                }
            }
            .then(
                if (isFocused) {
                    Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(4.dp),
                    )
                } else {
                    Modifier
                }
            ),
    ) {
        content()
    }
}

val DefaultKeymap: Map<Key, RetroPadButton> = mapOf(
    Key.DirectionUp to RetroPadButton.UP,
    Key.DirectionDown to RetroPadButton.DOWN,
    Key.DirectionLeft to RetroPadButton.LEFT,
    Key.DirectionRight to RetroPadButton.RIGHT,
    Key.Z to RetroPadButton.A,
    Key.X to RetroPadButton.B,
    Key.Enter to RetroPadButton.START,
    Key.Backspace to RetroPadButton.SELECT,
    Key.A to RetroPadButton.L,
    Key.S to RetroPadButton.R,
)
