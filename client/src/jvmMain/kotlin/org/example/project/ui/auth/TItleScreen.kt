package org.example.project.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.io.File

@Composable
fun TitleScreen(
    onLoginClick: () -> Unit,
    onRegisterClick: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Background image with blur overlay

        val bitmap = remember {
            val file =
                File("/Users/edwinigbinoba/Documents/code/True-Dualocke/client/resources/kanto.png")
            org.jetbrains.skia.Image.makeFromEncoded(file.readBytes()).toComposeImageBitmap()
        }

        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .blur(20.dp)
        )

        // Dark overlay to make text readable
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
        )

        // Content
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(48.dp)
        ) {
            Text(
                "DUALOCKE LEAGUE",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
            Text(
                "Nuzlocke Tracker & Matchmaking",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = onLoginClick,
                modifier = Modifier.fillMaxWidth(0.5f).height(52.dp)
            ) {
                Text("Login", fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = onRegisterClick,
                modifier = Modifier.fillMaxWidth(0.5f).height(52.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White
                ),
                border = BorderStroke(1.dp, Color.White)
            ) {
                Text("Create Account", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun AuthBackground(content: @Composable BoxScope.() -> Unit) {
    val bitmap = remember {
        val file = File("/Users/edwinigbinoba/Documents/code/True-Dualocke/client/resources/kanto.png")
        org.jetbrains.skia.Image.makeFromEncoded(file.readBytes()).toComposeImageBitmap()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().blur(20.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
        )
        content()
    }
}