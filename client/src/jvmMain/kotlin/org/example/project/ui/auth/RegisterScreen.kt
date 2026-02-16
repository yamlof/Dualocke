package org.example.project.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.example.project.SupabaseClient

@Composable
fun RegisterScreen(
    onRegisterSuccess: () -> Unit
){

    var email by remember { mutableStateOf("") }
    var password by remember {mutableStateOf("")}
    var confirmPassword by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Register",style = MaterialTheme.typography.h1)

        TextField(
            value = email,
            onValueChange = {email = it},
            label = { Text("Email") }
        )

        TextField(
            value = username,
            onValueChange = {username = it},
            label = {Text("Username")}
        )

        TextField(
            value = password,
            onValueChange = {password = it},
            label = {Text("Password")},
            visualTransformation = PasswordVisualTransformation()
        )

        TextField(
            value = confirmPassword,
            onValueChange = {confirmPassword = it},
            label = {Text("Confirm Password")},
            visualTransformation = PasswordVisualTransformation()
        )

        Button(
            onClick = {
                if (password != confirmPassword){
                    error = "Passwords do not match"
                    return@Button
                }

                scope.launch {
                    try {
                        SupabaseClient.register(email,username,password)
                        onRegisterSuccess()
                    } catch (e: Exception){
                        error = e.message ?: "Registration Failed"
                    }
                }
            }
        ){
            Text("Register")
        }

        TextButton(onClick = onRegisterSuccess){
            Text("Already have an account? Click here")
        }

        if (error.isNotEmpty()) Text(error, color = Color.Red)
    }
}