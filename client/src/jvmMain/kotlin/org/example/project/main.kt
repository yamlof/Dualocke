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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import loadToken
import login
import register
import saveToken
import java.io.File

@Composable
fun AuthNav (

){

}

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "KotlinProject",
    ) {
        MaterialTheme{
            var email = remember { mutableStateOf("") }
            val confirmEmail = remember { mutableStateOf("") }
            var username = remember{ mutableStateOf("") }
            var password = remember{mutableStateOf("")}
            val confirmPassword = remember { mutableStateOf("") }
            var message = remember{mutableStateOf("")}
            var loggedIn = remember{mutableStateOf(loadToken() != null)}
            var isLoading = remember { mutableStateOf(false) }
            var isRegistering = remember { mutableStateOf(false) }

            val isEmailMatch = email.value == confirmEmail.value
            val isPasswordMatch = password.value == confirmPassword.value

            val scope = rememberCoroutineScope()

            val supabase = createSupabaseClient(
                supabaseKey = "sb_publishable_P6DJHLxxuvWKgmFcK5rS1w_PwQUsc49",
                supabaseUrl = "https://jeiuhnrcakstcwenurci.supabase.co"
            ){
                install(Auth)
                install(Postgrest)
            }
            val session = supabase.auth.currentSessionOrNull()


            if (session == null){
                AuthNav()
            } else {
                    HomeScreen(
                        onLogout ={
                            supabase.auth.signOut()
                        }
                )
            }

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
                        value = email.value,
                        onValueChange = {email.value = it},
                        label = { Text("Email") }
                    )
                    TextField(
                        value = confirmEmail.value,
                        onValueChange = {confirmEmail.value = it},
                        label = {Text("Confirm Email")}
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
                        modifier = Modifier,
                        visualTransformation = PasswordVisualTransformation()
                    )
                    TextField(
                        value = confirmPassword.value,
                        onValueChange = {confirmPassword.value = it},
                        label = {Text("Confirm Password")},
                        visualTransformation = PasswordVisualTransformation()
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
                        enabled = !isLoading.value && isEmailMatch && isPasswordMatch
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
        }
    }
}
