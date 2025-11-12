package org.example.project

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import loadToken
import login
import register
import saveToken
import java.nio.ByteBuffer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.painter.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.IRect
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.*
import java.nio.ByteOrder


object MgbaBridge {
    init {
        System.load("/home/hhgsxdesktop/Documents/code/Dualocke/jni/mgba/build/libmgba.so")
        System.load("/home/hhgsxdesktop/Documents/code/Dualocke/jni/libnative-mgba.so")

    }

    external fun init(): Boolean
    external fun loadRom(path: String): Boolean
    external fun runFrame()
    external fun getFramebuffer(): ByteBuffer
}

fun rgb565ToArgb8888(pixel: Short): Int {
    val p = pixel.toInt() and 0xFFFF
    val r = ((p shr 11) and 0x1F) * 255 / 31
    val g = ((p shr 5) and 0x3F) * 255 / 63
    val b = (p and 0x1F) * 255 / 31
    return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}

fun getFramePixels(): IntArray? {
    val buffer = MgbaBridge.getFramebuffer() ?: return null
    // Assuming standard GBA 240x160 resolution
    val width = 240
    val height = 160
    val pixels = IntArray(width * height)
    buffer.rewind()
    for (y in 0 until height) {
        for (x in 0 until width) {
            val idx = y * width + x
            val pixel = buffer.short
            pixels[idx] = rgb565ToArgb8888(pixel)
        }
    }
    return pixels
}

@Composable
fun App2() {
    val bitmap = remember {
        Bitmap().apply {
            allocPixels(ImageInfo.makeS32(240, 160, ColorAlphaType.PREMUL))
        }
    }
    val byteBuffer = remember { ByteBuffer.allocate(240 * 160 * 4).order(ByteOrder.nativeOrder()) }
    val pixels = remember { IntArray(240 * 160) }
    var triggerRedraw by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }  // Add this line!


    LaunchedEffect(Unit) {
        println("Starting emulator initialization...")

        val initResult = MgbaBridge.init()
        println("Init result: $initResult")
        if (!initResult) {
            error = "Failed to initialize mGBA"
            return@LaunchedEffect
        }

        val romPath = "/home/hhgsxdesktop/Documents/code/Dualocke/client/resources/roms/firered.gba" // Update this!
        println("Loading ROM: $romPath")

        val romFile = java.io.File(romPath)
        println("File exists: ${romFile.exists()}")
        println("File readable: ${romFile.canRead()}")
        println("File size: ${romFile.length()} bytes")
        println("Absolute path: ${romFile.absolutePath}")

        val loadResult = MgbaBridge.loadRom(romPath)
        println("Load result: $loadResult")
        if (!loadResult) {
            error = "Failed to load ROM: $romPath"
            return@LaunchedEffect
        }

        println("Starting frame loop...")
        var frameCount = 0
        while (true) {
            MgbaBridge.runFrame()

            val buffer = MgbaBridge.getFramebuffer()
            if (frameCount < 5) {
                println("Frame $frameCount - Buffer: $buffer, remaining: ${buffer?.remaining()}")
            }

            if (buffer != null) {
                buffer.rewind()

                for (i in pixels.indices) {
                    pixels[i] = rgb565ToArgb8888(buffer.short)
                }

                byteBuffer.clear()
                byteBuffer.asIntBuffer().put(pixels)

                bitmap.installPixels(byteBuffer.array())
                triggerRedraw++
            }

            frameCount++
            delay(16)
        }
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                triggerRedraw
                val image = Image.makeFromBitmap(bitmap).toComposeImageBitmap()
                drawImage(image, dstSize = IntSize(size.width.toInt(), size.height.toInt()))
            }
        }
    }
}

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "KotlinProject",
    ) {

       App2()
        /*
        MaterialTheme{
            var username = remember{ mutableStateOf("") }
            var password = remember{mutableStateOf("")}
            var message = remember{mutableStateOf("")}
            var loggedIn = remember{mutableStateOf(loadToken() != null)}
            var isLoading = remember { mutableStateOf(false) }
            var isRegistering = remember { mutableStateOf(false) }





            val scope = rememberCoroutineScope() // Proper lifecycle management


            if (!loggedIn.value) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {

                    Text(
                        if (isRegistering.value) "Register" else "Login",
                        style = MaterialTheme.typography.h1
                        )

                    TextField(
                        value = username.value,
                        onValueChange = { username.value = it },
                        label = { Text("Username") },
                    )
                    TextField(
                        value = password.value,
                        onValueChange = { password.value = it },
                        label = { Text("Password") },
                        modifier = Modifier
                    )

                    Button(
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                try {
                                    if (isRegistering.value) {
                                        register(username.value,password.value)
                                        message.value = "Registration Successful"
                                        isRegistering.value = false
                                    }else{
                                        val response = login(username.value,password.value)
                                        saveToken(response.token)
                                        loggedIn.value = true
                                        message.value = "Login successful"
                                    }

                                } catch (e: Exception) {
                                    message.value = "${e.message}"
                                } finally {
                                    isLoading.value = false
                                }
                            }
                        },
                        enabled = !isLoading.value
                    ) {
                        Text(if (isRegistering.value) "Register" else "Login")
                    }

                    TextButton(onClick = { isRegistering.value = !isRegistering.value }) {
                        Text(if (isRegistering.value) "Already have an account? Login" else "No account? Register")
                    }

                    if (message.value.isNotBlank()) Text(message.value)
                }
            } else{
                HomeScreen(
                    onLogout = {
                        saveToken("")
                        username.value = ""
                        password.value = ""
                        loggedIn.value = false
                    }
                )
            }
        }*/
    }
    }
