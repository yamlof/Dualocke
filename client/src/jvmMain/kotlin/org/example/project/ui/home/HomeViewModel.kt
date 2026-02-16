package org.example.project.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.example.project.data.network.LuaTcpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.example.project.SupabaseClient
import org.example.project.domain.models.PokemonResponse
import org.example.project.launchMGBA
import java.lang.Exception

class HomeViewModel : ViewModel(){
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val tcpClient = LuaTcpClient()
    private var connectionJob: Job? = null

    private val pokeApiClient = HttpClient(CIO){
        install(Logging){
            filter { request ->
                request.url.host.contains("pokeapi.co")
            }
        }
        install(ContentNegotiation){
            json(Json {
                ignoreUnknownKeys = true
            })
        }
    }

    init {
        loadUserProfile()
        loadSaveFileData()
    }

    private fun loadUserProfile(){
        viewModelScope.launch {
            try {
                val profile = SupabaseClient.getUsername()
                _uiState.update { it.copy(
                    username = profile?.username,
                    isLoading = false
                ) }
            }catch (e: Exception){
                _uiState.update { it.copy(
                    username = null,
                    isLoading = false
                ) }
            }
        }
    }

    private fun loadSaveFileData(){
        viewModelScope.launch {
            try {
                val saveData = readSaveFile("resources/roms/firered.save")
                val saveBase = getActiveFireRedSaveBase(saveData)

                val trainerSection = findSection(saveData,saveBase,0)
                val teamItemSection = findSection(saveData,saveBase,1)
                val teamSizeBytes = saveData.copyOfRange(
                    teamItemSection + 0x0034,
                    teamItemSection + 0x0034 + 1
                )
                val teamBytes = saveData.copyOfRange(
                    teamItemSection + 0x0038,
                    teamItemSection + 0x0038 + 600
                )
                val nameBytes = saveData.copyOfRange(trainerSection, trainerSection + 7)

                val trainerName = decodeFRString(nameBytes)
                val teamSize = teamSizeBytes.joinToString(" ") { it.toString() }.toInt()

                // Load Pokemon team
                val pokemonTeam = buildList {
                    for (i in 0 until teamSize) {
                        val start = i * 100
                        val end = (i * 100) + 100
                        add(teamBytes.copyOfRange(start, end))
                    }
                }

                // Load Pokemon icons
                val icons = loadPokemonIcons(pokemonTeam)

                _uiState.update { it.copy(
                    trainerName = trainerName,
                    pokemonTeamIcons = icons,
                    gameName = "Pokémon FireRed"
                )}
            } catch (e: Exception) {
                println("Error loading save file: ${e.message}")
            }
        }
    }

    private suspend fun loadPokemonIcons(pokemonTeam: List<ByteArray>): List<String> {
        return runBlocking {
            pokemonTeam.mapNotNull { pokemon ->
                try {
                    val pokemonBytesName = pokemon.copyOfRange(0x08, 0x08 + 10)
                    val name = decodeFRString(pokemonBytesName)
                    val formattedName = name.trim().lowercase()

                    println("Loading icon for: $formattedName")

                    val response = pokeApiClient.get(
                        "https://pokeapi.co/api/v2/pokemon/$formattedName"
                    )
                    val pokemonResponse: PokemonResponse = response.body()
                    pokemonResponse.sprites.versions.generation7.icons.front_default
                } catch (e: Exception) {
                    println("Error loading pokemon icon: ${e.message}")
                    null
                }
            }
        }
    }

    fun startTcpConnection() {
        connectionJob = viewModelScope.launch {
            tcpClient.connect(
                onLine = { line ->
                    _uiState.update {
                        it.copy(
                            partyLines = (it.partyLines + line).takeLast(100),
                            isConnected = true
                        )
                    }
                },
                onError = { error ->
                    _uiState.update {
                        it.copy(
                            connectionError = "Connection lost: ${error.message}",
                            isConnected = false
                        )
                    }
                }
            )
        }
    }

    fun stopTcpConnection() {
        connectionJob?.cancel()
        tcpClient.disconnect()
        _uiState.update { it.copy(isConnected = false) }
    }

    fun launchGame() {
        launchMGBA()
    }

    fun findCasualMatch() {
        // TODO: Implement casual match finding
        println("Finding casual match...")
    }

    fun findRankedMatch() {
        // TODO: Implement ranked match finding
        println("Finding ranked match...")
    }

    fun changeRun() {
        // TODO: Implement run selection
        println("Changing run...")
    }

    fun viewLeaderboards() {
        // TODO: Navigate to leaderboards
        println("Viewing leaderboards...")
    }

    fun viewHistory() {
        // TODO: Navigate to match history
        println("Viewing history...")
    }

    fun viewCommunity() {
        // TODO: Navigate to community
        println("Viewing community...")
    }

    fun openSettings() {
        // TODO: Navigate to settings
        println("Opening settings...")
    }

    override fun onCleared() {
        super.onCleared()
        pokeApiClient.close()
        tcpClient.disconnect()
    }
}