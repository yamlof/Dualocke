package org.example.project

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.awt.image.BufferedImage

@Composable
fun EmulatorScreen(core: LibretroCore) {
    var imageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            val pixels = core.runFrame()
            val width = core.frameWidth
            val height = core.frameHeight

            if (width > 0 && height > 0) {
                imageBitmap = pixelsToImageBitmap(pixels, width, height)
            }
            delay(16)
        }
    }

    imageBitmap?.let { img ->
        Image(
            painter = BitmapPainter(img),
            contentDescription = "Game",
            modifier = Modifier.size(480.dp, 320.dp)
        )
    }
}

private fun pixelsToImageBitmap(pixels: IntArray, width: Int, height: Int): ImageBitmap {
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    image.setRGB(0, 0, width, height, pixels, 0, width)
    return image.toComposeImageBitmap()
}