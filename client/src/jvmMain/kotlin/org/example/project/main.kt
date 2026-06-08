package org.example.project

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import org.example.project.ui.auth.LoginScreen
import org.example.project.ui.auth.RegisterScreen
import org.example.project.ui.auth.TitleScreen
import org.example.project.ui.home.HomeScreen
import org.example.project.ui.home.HomeViewModel

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

@Serializable object EmulatorScreenDestination   // ← new

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Dualocke",
    ) {
        // App-level container — created once, lives for the whole window.
        val appContainer = remember { AppContainer() }

        // Make sure native resources are freed if the window closes.
        DisposableEffect(Unit) {
            onDispose { appContainer.dispose() }
        }

        // HomeViewModel is hoisted up here so it survives navigation to
        // EmulatorScreen and back. Compose Navigation already keeps Home on
        // the back stack, but by creating the VM here we can pass the
        // session through cleanly.
        val homeViewModel = remember { HomeViewModel(appContainer.emulatorSession) }

        val navController = rememberNavController()
        MaterialTheme {
            NavHost(navController = navController, startDestination = TitleScreenDestination) {
                composable<TitleScreenDestination> {
                    TitleScreen(
                        onLoginClick = { navController.navigate(LoginScreenDestination) },
                        onRegisterClick = { navController.navigate(RegisterScreenDestination) }
                    )
                }
                composable<LoginScreenDestination> {
                    LoginScreen(
                        onLoginSuccess = {
                            navController.navigate(HomeScreenDestination) {
                                popUpTo<TitleScreenDestination> { inclusive = true }
                            }
                        },
                        onRegisterClick = { navController.navigate(RegisterScreenDestination) }
                    )
                }
                composable<RegisterScreenDestination> {
                    RegisterScreen(
                        onRegisterSuccess = {
                            navController.navigate(HomeScreenDestination) {
                                popUpTo<TitleScreenDestination> { inclusive = true }
                            }
                        }
                    )
                }
                composable<HomeScreenDestination> {
                    HomeScreen(
                        viewModel = homeViewModel,
                        onLogout = {
                            navController.navigate(TitleScreenDestination) {
                                popUpTo<HomeScreenDestination> { inclusive = true }
                            }
                        },
                        onLaunchEmulator = {
                            navController.navigate(EmulatorScreenDestination)
                        },
                    )
                }
                composable<EmulatorScreenDestination> {
                    EmulatorScreen(
                        session = appContainer.emulatorSession,
                        onBack = { navController.popBackStack() },
                        onQuitGame = {
                            // Session was stopped inside EmulatorScreen.
                            navController.popBackStack()
                        },
                    )
                }
            }
        }
    }
}
