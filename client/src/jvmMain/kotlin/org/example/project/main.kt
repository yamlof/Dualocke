package org.example.project

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextField
import androidx.compose.material.rememberBottomSheetState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import loadToken
import login
import register
import saveToken
import kotlin.math.log

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "KotlinProject",
    ) {
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
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
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
                        modifier = Modifier.fillMaxWidth()
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
                Column {
                    Text("You are logged in")
                    Button(onClick = {
                        saveToken("")
                        loggedIn.value = false
                    }){
                        Text("Logout")
                    }
                }
            }
        }
    }
}