package org.example.project.ui.home

import androidx.compose.animation.core.snap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.serialization.json.Json
import org.example.project.SupabaseClient
import org.example.project.data.RunRepository
import org.example.project.data.network.NuzlockeSnapshot
import org.example.project.domain.models.PokemonResponse
import org.example.project.domain.models.PokemonTeamMember
import org.example.project.launchMGBA
import java.io.File
import java.lang.Exception

class HomeViewModel (
    val runRepository: RunRepository = RunRepository()
): ViewModel() {
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
        observeCurrentRun()
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

    private fun observeCurrentRun(){
        viewModelScope.launch {
                runRepository.currentRun.collect{run ->
                    if (run != null){
                        loadRunData(run.id)
                    }else{
                        _uiState.update { it.copy(
                            trainerName = "No run selected",
                            pokemonTeamIcons = emptyList(),
                            badges = "0/8",
                            deaths = "0"
                        ) }
                    }
                }
        }
    }

    private fun loadRunData(runId : String){
        viewModelScope.launch {
            try {
                val run = runRepository.allRuns.value.firstOrNull{it.id == runId}
                if (run == null){
                    println("Run not found: $runId")
                    return@launch
                }

                val result = runRepository.loadRunData(run)
                result.onSuccess { runData ->
                    val icons = loadPokemonIcons(runData.teamMembers)

                    val updatedTeam = runData.teamMembers.mapIndexed { index, member ->
                        member.copy(iconUrl = icons.getOrNull(index) ?: "")
                    }

                    runRepository.updateRunTeam(run.id,updatedTeam)

                    _uiState.update { it.copy(
                        trainerName = runData.trainerName,
                        gameName = run.gameName,
                        badges = "${run.badges}/8",
                        deaths = run.deaths.toString(),
                        pokemonTeamIcons = icons,
                        currentRunId = run.id
                    ) }
                }.onFailure { error ->
                    println("Error loading run data: ${error.message}")
                }
            }catch (e: kotlin.Exception){
                println("Error in loadRunData: ${e.message}")
            }
        }
    }

    private suspend fun loadPokemonIcons(teamMembers: List<PokemonTeamMember>): List<String> {
        return teamMembers.mapNotNull { member ->
                try {
                    val formattedName = member.species.trim().lowercase()
                    println("Loading icon for: $formattedName")

                    val response = pokeApiClient.get(
                        "https://pokeapi.co/api/v2/pokemon/$formattedName"
                    )
                    val pokemonResponse: PokemonResponse = response.body()
                    pokemonResponse.sprites.versions.generation7.icons.front_default
                } catch (e: Exception) {
                    println("Error loading pokemon icon for ${member.species}: ${e.message}")
                    null
                }
            }
    }

    fun createNewRun(saveFilePath: String){
        viewModelScope.launch {
            val result = runRepository.createRun(saveFilePath)
            result.onSuccess { run ->
                println("Created New Run : ${run.trainerName}")
            }.onFailure { error ->
                println("Error creating run : ${error.message}")
            }
        }
    }

    fun switchRun(runId: String){
        viewModelScope.launch {
            // Save current run's emulator state before switching
            val currentRunId = uiState.value.currentRunId
            if (currentRunId != null) {
                runRepository.syncRunFromEmulator(currentRunId)
                    .onFailure { println("Sync from emulator skipped: ${it.message}") }
            }
            runRepository.setActiveRun(runId)
        }
    }

    fun setEmulatorSavePath(runId: String, emulatorSavePath: String) {
        runRepository.setEmulatorPaths(runId, emulatorSavePath)
        _uiState.update { it.copy(showEmulatorPathPrompt = false) }
    }

    fun changeRun(){
        // TODO: Naviggate to trun selection process
        println("Opening run selection..")

        runRepository.allRuns.value.forEach { run ->
            println("Run : ${run.trainerName} (${run.gameName}) - Active: ${run.isActive}")
        }
    }

    fun startTcpConnection() {
        connectionJob = viewModelScope.launch {
            val runId = uiState.value.currentRunId

            tcpClient.connect(
                runId = runId,
                onLine = { line ->
                    // Update party lines for live display
                    _uiState.update {
                        it.copy(
                            partyLines = (it.partyLines + line).takeLast(100)
                        )
                    }
                },
                onSnapshot = { snapshot ->
                    handleSnapshot(snapshot)
                },
                onError = { error ->
                    println("Connection Error: ${error.message}")
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

    private fun handleSnapshot(snapshot: NuzlockeSnapshot) {
        println("📸 Snapshot #${snapshot.sequence}")
        println("   Badges: ${snapshot.badgeCount}/8")
        println("   Party: ${snapshot.party.size}")
        println("   Deaths: ${snapshot.deaths.size}")

        val currentRunId = uiState.value.currentRunId ?: return

        // Update repository stats
        runRepository.updateRunStats(
            runId = currentRunId,
            badges = snapshot.badgeCount,
            deaths = snapshot.deaths.size
        )

        // Update UI state with live data
        _uiState.update { state ->
            state.copy(
                badges = "${snapshot.badgeCount}/8",
                deaths = snapshot.deaths.size.toString(),
                isConnected = true,
                connectionError = null,
            )
        }

        // Log deaths as they happen
        snapshot.deaths.forEach { death ->
            println("☠️ ${death.nickname} (${death.species}) Lv${death.level} died at location ${death.location}")
        }
    }



    fun importRom(sourceFile: File) {
        viewModelScope.launch {
            val result = runRepository.importRom(sourceFile)
            result.onSuccess { run ->
                println("Successfully imported: ${run.trainerName}")
                // Run is now active and will auto-load
            }.onFailure { error ->
                println("Error importing ROM: ${error.message}")
                // TODO: Show error to user
            }
        }
    }

    /**
     * Delete a run
     */
    fun deleteRun(runId: String) {
        runRepository.deleteRun(runId, deleteFiles = false)
        // Set deleteFiles = true if you want to also delete the save file
    }

    fun stopTcpConnection() {
        connectionJob?.cancel()
        tcpClient.disconnect()
        _uiState.update { it.copy(isConnected = false) }
    }

    fun launchGame() {
        viewModelScope.launch {
            val currentRunId = uiState.value.currentRunId
            if (currentRunId != null) {
                runRepository.syncRunFromEmulator(currentRunId)
                    .onFailure { println("No Emulator save to pull(first launch is fine):${it.message}") }
            }

            if (currentRunId != null){
                runRepository.syncRunToEmulator(currentRunId)
                    .onFailure {
                        _uiState.update { it.copy(showEmulatorPathPrompt = true) }
                        return@launch
                    }
            }
            launchMGBA()
        }
    }

    fun findCasualMatch() {
        // TODO: Implement casual match finding
        println("Finding casual match...")
    }

    fun findRankedMatch() {
        // TODO: Implement ranked match finding
        println("Finding ranked match...")
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