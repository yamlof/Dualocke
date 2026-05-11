package org.example.project

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import org.example.project.ui.auth.LoginScreen
import org.example.project.ui.auth.RegisterScreen
import org.example.project.ui.auth.TitleScreen
import org.example.project.ui.home.HomeScreen
import org.example.project.data.network.SupabaseClient

@Serializable
object LoginScreenDestination

@Serializable
object RegisterScreenDestination

@Serializable
object HomeScreenDestination

// Data classes for matchmaking

@Serializable
data class MatchQueueInsert(
   val player_id: String,
    val badge_count: Int,
    val team: JsonArray,
    val deaths: Int = 0,
    val elo: Int = 1000,
    val is_bot: Boolean = false
)

@Serializable
data class MatchQueueEntry(
    val player_id: String,
    val badge_count: Int,
    val team: JsonArray,
    val deaths: Int = 0,
    val elo: Int = 1000,
    val is_bot: Boolean = false,
    val id: String? = null
)

@Serializable
data class MatchInsert(
    val player1_id: String,
    val player2_id: String,
    val player1_team: JsonArray,
    val player2_team: JsonArray,
    val player1_deaths: Int = 0,
    val player2_deaths: Int = 0,
    val badge_count: Int,
    val player1_elo_before: Int = 1000,
    val player2_elo_before: Int = 1000,
    val status: String = "pending"
)

@Serializable
data class Match(
    val id: String? = null,
    val player1_id: String,
    val player2_id: String,
    val player1_team: JsonArray,
    val player2_team: JsonArray,
    val player1_deaths: Int = 0,
    val player2_deaths: Int = 0,
    val badge_count: Int,
    val showdown_room_id: String? = null,
    val winner_id: String? = null,
    val player1_elo_before: Int = 1000,
    val player2_elo_before: Int = 1000,
    val player1_elo_after: Int? = null,
    val player2_elo_after: Int? = null,
    val status: String = "pending"
)

@Serializable
data class PlayerRating(
    val id: String? = null,
    val player_id: String,
    val elo: Int = 1000,
    val matches_played: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0
)

@Serializable
data class PlayerRatingInsert(
    val player_id: String,
    val elo: Int = 1000,
    val matches_played: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0
)

@Serializable
data class Profile (
    val id : String,
    val email : String,
    val username : String
)

@Serializable
data class LeaderboardEntry(
    val player_id: String,
    val elo: Int,
    val matches_played: Int,
    val wins: Int,
    val losses: Int,
    val username: String = ""
)

@Serializable object TitleScreenDestination


fun main() = application {

    LaunchedEffect(Unit) {
        SupabaseClient.initializeSession()
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "Dualocke",
    ) {


        MaterialTheme {
            val navController = rememberNavController()
            val scope = rememberCoroutineScope()
            val startDestination = TitleScreenDestination

            NavHost(navController = navController, startDestination = startDestination) {
                composable<TitleScreenDestination> {
                    TitleScreen(
                        onLoginClick = {
                            navController.navigate(LoginScreenDestination)
                        },
                        onRegisterClick = {
                            navController.navigate(RegisterScreenDestination)
                        },
                    )
                }
                composable<LoginScreenDestination> {
                    LoginScreen(
                        onLoginSuccess = {
                            navController.navigate(HomeScreenDestination) {
                                popUpTo(TitleScreenDestination) { inclusive = true }
                            }
                        },
                        onRegisterClick = {
                            navController.navigate(RegisterScreenDestination)
                        }
                    )
                }

                composable<RegisterScreenDestination> {
                    RegisterScreen(
                        onRegisterSuccess = {
                            navController.navigate(HomeScreenDestination) {
                                popUpTo(TitleScreenDestination) { inclusive = true }
                            }
                        }
                    )
                }

                composable<HomeScreenDestination> {
                    HomeScreen(
                        onLogout = {
                            scope.launch {
                                SupabaseClient.logout()
                                navController.navigate(TitleScreenDestination) {
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