package org.example.project

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.example.project.ui.auth.LoginScreen
import org.example.project.ui.auth.RegisterScreen
import org.example.project.ui.home.HomeScreen
import org.example.project.ui.home.rememberPartyViewModel


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
            val viewModel = rememberPartyViewModel()
            val isMgbaRunning by viewModel.isMgbaRunning.collectAsState()
            val partyLines by viewModel.partyLines.collectAsState()

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
                                navController.navigate(LoginScreenDestination) {
                                    popUpTo(HomeScreenDestination) { inclusive = true }
                                }
                            }
                        },
                        isMgbaRunning = isMgbaRunning
                    )
                }
            }
        }
    }
}