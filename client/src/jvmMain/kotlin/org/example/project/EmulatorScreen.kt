package org.example.project

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.example.project.emulator.EmulatorSession

/**
 * Dedicated full-window emulator screen.
 *
 * Layout: a top bar (Back + Quit Game), the rest is the emulator panel
 * fitted to the available space at GBA aspect (240x160).
 *
 * Behaviour:
 *   - Back: returns to Home WITHOUT stopping the emulator. The session
 *     keeps running so the user can navigate away and come back.
 *   - Quit Game: stops the emulator AND returns to Home. Use this to
 *     unload the game (e.g. before launching a different ROM).
 *
 * The session is passed in directly — this screen does not own it.
 * It's owned by AppContainer and lives across screens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmulatorScreen(
    session: EmulatorSession,
    onBack: () -> Unit,
    onQuitGame: () -> Unit,
) {
    val frame by session.frame.collectAsState()
    val state by session.state.collectAsState()
    val speed by session.speedMultiplier.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Playing", fontWeight = FontWeight.SemiBold)
                    if (speed > 1) {
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "▶▶ ${speed}x",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                TextButton(
                    onClick = {
                        session.stop()
                        onQuitGame()
                    },
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Quit Game")
                }
            },
        )

        // Emulator fills the rest of the window, centred, at GBA aspect ratio.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            EmulatorInput(
                session = session,
                modifier = Modifier
                    .fillMaxSize()
                    .aspectRatio(240f / 160f),
            ) {
                val bmp = frame
                if (bmp != null) {
                    Image(
                        painter = BitmapPainter(bmp),
                        contentDescription = "Game",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    // Pre-first-frame placeholder.
                    Text(
                        when (state) {
                            EmulatorSession.State.IDLE -> "No game loaded"
                            EmulatorSession.State.READY -> "Starting…"
                            EmulatorSession.State.RUNNING -> "Loading…"
                            EmulatorSession.State.STOPPED -> "Stopped"
                        },
                        color = Color.White,
                    )
                }
            }
        }
    }
}