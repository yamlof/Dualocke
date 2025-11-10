package org.example.project

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import kotlinx.coroutines.withContext


fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "KotlinProject",
    ) {

        var frame by remember { mutableStateOf<ByteBuffer?>(null) }

        LaunchedEffect(Unit) {
            withContext(Dispatchers.Default) {
                MgbaBridge.init()
                MgbaBridge.loadRom("/Users/edwinigbinoba/Documents/code/Dualocke/client/resources/roms/Pokemon - FireRed Version (USA, Europe) (Rev 1).gba")
                while (true) {
                    MgbaBridge.step()
                    frame = MgbaBridge.getFramebuffer()
                }
            }
        }

        frame?.let { GbaScreen(it) }

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
