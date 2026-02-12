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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import loadToken
import login
import register
import saveToken
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer
import kotlinx.serialization.json.Json


@Serializable
object LoginScreenDestination

@Serializable
object RegisterScreenDestination

@Serializable
object HomeScreenDestination

@Serializable
data class Profile (
    val id : String,
    val email : String,
    val username : String
)


object SupabaseClient {
    val client = createSupabaseClient(
        supabaseKey = "sb_publishable_P6DJHLxxuvWKgmFcK5rS1w_PwQUsc49",
        supabaseUrl = "https://jeiuhnrcakstcwenurci.supabase.co"
    ){
        install(Auth)
        install(Postgrest)

        defaultSerializer = KotlinXSerializer(Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
        })
    }

    suspend fun register (email:String, username : String,password:String, ) {
        val result = client.auth.signUpWith(Email){
            this.email = email
            this.password = password
        }

        val userId = client.auth.currentUserOrNull()?.id ?: error("user not available")

        client.from("profiles")
            .insert(
                Profile(
                    id = userId,
                    email = email,
                    username = username
                )
            )
    }

    suspend fun login (email: String, password: String ){
        client.auth.signInWith(Email){
            this.email = email
            this.password = password
        }
    }

    suspend fun logout() {
        client.auth.signOut()
    }

    suspend fun getUsername(): Profile? {
        val user = client.auth.currentUserOrNull() ?: return null

        return client
            .from("profiles")
            .select {
                filter {
                    eq("id",user.id)
                }
            }
            .decodeSingleOrNull<Profile>()

    }

    fun session() = client.auth.currentSessionOrNull()
}

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Dualocke",
    ) {
        MaterialTheme {

            val navController = rememberNavController()
            val scope = rememberCoroutineScope()

            val startDestination = if (SupabaseClient.session() == null)
                LoginScreenDestination else HomeScreenDestination

            NavHost(navController = navController, startDestination = startDestination) {
                composable<LoginScreenDestination> {
                    LoginScreen(
                        onLoginSuccess = {
                            navController.navigate(HomeScreenDestination)
                        },
                        onRegisterClick = {
                            navController.navigate(RegisterScreenDestination)
                        }
                    )
                }

                composable<RegisterScreenDestination> {
                    RegisterScreen(
                        onRegisterSuccess = {
                            navController.popBackStack()
                        }
                    )
                }

                composable<HomeScreenDestination> {
                    HomeScreen(
                        onLogout = {
                            scope.launch {
                                SupabaseClient.logout()
                                navController.navigate(LoginScreenDestination){
                                    popUpTo(HomeScreenDestination) { inclusive = true }
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}