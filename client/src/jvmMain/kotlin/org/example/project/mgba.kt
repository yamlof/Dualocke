package org.example.project

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import java.awt.image.BufferedImage
import java.nio.ByteBuffer
import java.nio.IntBuffer


object MgbaBridge {
    init {
        // Load your native library
        System.loadLibrary("mgba_jni") // looks for libmgba_jni.so/.dylib/.dll
    }

    external fun init()
    external fun loadRom(path: String)
    external fun step()
    external fun getFramebuffer(): java.nio.ByteBuffer
}


@Composable
fun GbaScreen(buffer: ByteBuffer) {
    val width = 240
    val height = 160

    // Convert framebuffer to IntArray
    val pixels = IntArray(width * height)
    buffer.asIntBuffer().get(pixels)

    // Create BufferedImage and set pixels
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    image.setRGB(0, 0, width, height, pixels, 0, width)

    // Convert BufferedImage to Compose ImageBitmap
    val bitmap: ImageBitmap = image.toComposeImageBitmap()

    // Display
    Image(painter = BitmapPainter(bitmap), contentDescription = null)
}