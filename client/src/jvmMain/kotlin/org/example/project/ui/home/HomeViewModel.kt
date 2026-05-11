package org.example.project.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jan.supabase.auth.auth
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import org.example.project.Match
import org.example.project.data.FilePathProvider
import org.example.project.data.GbaFileWatcher
import org.example.project.data.GbaRunManager
import org.example.project.data.GbaValidator
import org.example.project.data.RunRepository
import org.example.project.data.UserSession
import org.example.project.data.network.NuzlockeSnapshot
import org.example.project.data.network.ShowdownClient
import org.example.project.data.network.SupabaseClient
import org.example.project.domain.models.GameVersion
import org.example.project.domain.models.PokemonResponse
import org.example.project.domain.models.PokemonRun
import org.example.project.domain.models.PokemonTeamMember
import org.example.project.launchMGBA
import org.example.project.ui.home.components.BrowserLauncher
import org.example.project.utils.getRankFromElo
import java.io.File
import java.lang.Exception

class HomeViewModel : ViewModel() {

    val runRepository = RunRepository
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val tcpClient = LuaTcpClient()
    private var connectionJob: Job? = null

    private var activeRunFolder: File? = null
    private var gbaWatcher: GbaFileWatcher? = null
    private var isSwitching = false

    private var currentMatch: Match? = null

    private val showdownClient = ShowdownClient()
    private var showdownJob: Job? = null

    private val deathTimestamps = mutableMapOf<String, Long>()
    private val DECAY_INTERVAL_MS = 60_000L
    private val DECAY_AMOUNT = 2
    private val processedDeaths = mutableSetOf<String>()

    private var baselineDeathCount: Int? = null


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
        runRepository.reloadForUser()
        loadUserProfile()
        observeCurrentRun()
        checkExistingRom()
        loadVerifiedGames()
        loadPlayerRating()
        observeUserChanges()
    }

    private fun observeUserChanges() {
        viewModelScope.launch {
            UserSession.currentUserId.drop(1).collect { userId ->
                // drop(1) skips the initial value so we only react to actual changes
                println("👤 User changed to: $userId — resetting state")

                // Stop everything tied to the old user
                stopTcpConnection()
                gbaWatcher?.stop()
                gbaWatcher = null
                activeRunFolder = null
                clearDeathTracking()

                // Clear UI state
                _uiState.update { it.copy(
                    currentRunId = null,
                    activeGbaRun = null,
                    activeGame = null,
                    trainerName = "No run selected",
                    username = null,
                    badges = "0/8",
                    deaths = "0",
                    pokemonTeamIcons = emptyList(),
                    partyLines = emptyList()
                ) }

                // Reload everything for the new user
                runRepository.reloadForUser()
                loadUserProfile()
                loadPlayerRating()
                checkExistingRom()
                loadVerifiedGames()
            }
        }
    }

    private fun loadUserProfile(){
        viewModelScope.launch {
            try {
                val profile = SupabaseClient.getUsername()
                if (profile != null) {
                    _uiState.update { it.copy(
                        username = profile.username,
                        isLoading = false
                    ) }
                } else {
                    // No profile returned — could be no session, or row missing
                    // Don't wipe existing username; just stop loading
                    _uiState.update { it.copy(isLoading = false) }
                }
            } catch (e: Exception) {
                println("⚠️ Failed to load profile: ${e.message}")
                // Network/server error — keep whatever username we had
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun updateShowdownUsername(name: String) {
        _uiState.update { it.copy(showdownUsername = name) }
    }

    private fun observeCurrentRun() {
        viewModelScope.launch {
            var lastRunId : String? = null
            runRepository.currentRun.collect { run ->

                if (run?.id != lastRunId){
                    clearDeathTracking()
                    lastRunId = run?.id
                }

                // Update activeRunFolder to match the current run
                activeRunFolder = run?.let { findRunFolderForRun(it) }
                println("📁 activeRunFolder is now: ${activeRunFolder?.absolutePath}")

                if (run != null) {
                    val activeSav = FilePathProvider.getActiveSavFile()
                    if (activeSav != null && activeSav.exists()) {
                        loadRunData(run.id)
                    } else {
                        _uiState.update { it.copy(
                            trainerName = "New Run",
                            badges = "0/8",
                            deaths = "0",
                            pokemonTeamIcons = emptyList()
                        ) }
                    }
                } else {
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

    private fun findRunFolderForRun(run: PokemonRun): File? {
        // Search the user-specific runs directory for the folder matching this run ID
        val runsDir = FilePathProvider.getRunsDirectory(run.gameVersion)
        if (!runsDir.exists()) return null

        return runsDir.listFiles()?.firstOrNull { folder ->
            val runIdFile = File(folder, "runid.txt")
            runIdFile.exists() && runIdFile.readText().trim() == run.id
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

    fun startTcpConnection() {
        connectionJob?.cancel()
        connectionJob = viewModelScope.launch {
            val runId = uiState.value.currentRunId
            tcpClient.startWithReconnect(
                scope = this,
                runId = runId,
                onLine = { line ->
                    _uiState.update {
                        it.copy(partyLines = (it.partyLines + line).takeLast(100))
                    }
                },
                onSnapshot = { snapshot -> handleSnapshot(snapshot) },
                onConnected = {
                    _uiState.update { it.copy(isConnected = true, connectionError = null) }
                },
                onDisconnected = {
                    _uiState.update { it.copy(isConnected = false) }
                }
            )
        }
    }

    private fun handleSnapshot(snapshot: NuzlockeSnapshot) {


        if (baselineDeathCount == null) {
            baselineDeathCount = snapshot.deaths.size
            snapshot.deaths.forEach { death ->
                val key = "${death.nickname}_${death.species}_${death.level}"
                processedDeaths.add(key)
            }
            println("📍 Baseline: ${snapshot.deaths.size} existing deaths recorded")
        }

        val previousBadges = uiState.value.badges.substringBefore("/").toIntOrNull() ?: 0
        val previousDeaths = uiState.value.deaths.toIntOrNull() ?: 0

        _uiState.update { state ->
            state.copy(
                latestSnapshot = snapshot,
                badges = "${snapshot.badgeCount}/8",
                deaths = snapshot.deaths.size.toString(),
                isConnected = true,
                connectionError = null
            )
        }

        snapshot.encounters.forEach { (mapId, enc) ->
            println("🗺️ Map $mapId: ${enc.species} - ${enc.status}")
        }

        val currentDeaths = snapshot.deaths.size


        if (currentDeaths > previousDeaths) {
            val newDeaths = snapshot.deaths.filter { death ->
                val key = "${death.nickname}_${death.species}_${death.level}"
                key !in processedDeaths
            }

            if (newDeaths.isNotEmpty()) {
                // Was this run "fresh" (no deaths processed yet) before this batch?
                val wasFirstEverDeath = processedDeaths.isEmpty()

                // Mark them processed
                newDeaths.forEach { death ->
                    val key = "${death.nickname}_${death.species}_${death.level}"
                    processedDeaths.add(key)
                }

                val starterSpecies = listOf("bulbasaur", "charmander", "squirtle", "pikachu")
                val dyingPokemon = newDeaths.lastOrNull()
                val isStarter = dyingPokemon?.species?.lowercase()?.trim() in starterSpecies

                if (wasFirstEverDeath && isStarter) {
                    println("Starter fainted on first death — penalty ignored")
                } else {
                    viewModelScope.launch {
                        val userId = UserSession.currentUserId.value ?: return@launch
                        val penalty = newDeaths.size * 10
                        val newElo = SupabaseClient.applyEloDelta(userId, -penalty)
                        loadPlayerRating()
                        println("Death penalty: -$penalty Elo, new Elo: $newElo")
                        _uiState.update { it.copy(gbaError = "Pokemon fainted! -${penalty} Elo") }
                    }
                }
            }
        }

        val currentTime = System.currentTimeMillis()
        snapshot.deaths.forEach { death ->
            val key = "${death.nickname}_${death.species}"
            val timestamp = deathTimestamps.getOrPut(key) { currentTime }
            val minutesElapsed = (currentTime - timestamp) / DECAY_INTERVAL_MS

            if (minutesElapsed > 0 && snapshot.party.any { it.nickname == death.nickname }) {
                deathTimestamps[key] = currentTime
                viewModelScope.launch {
                    val userId = UserSession.currentUserId.value ?: return@launch
                    val penalty = (minutesElapsed * DECAY_AMOUNT).toInt()
                    SupabaseClient.applyEloDelta(userId, -penalty)
                    loadPlayerRating()
                    println("Decay penalty: -$penalty Elo for keeping fainted pokemon in party")
                }
            }
        }

        val currentRunId = uiState.value.currentRunId ?: return


        if (snapshot.badgeCount > previousBadges) {
            println(" Badge gained! Party size: ${snapshot.party.size}")
            snapshot.party.forEach { println("  - ${it.nickname} ${it.species} Lv${it.level}") }

            val currentRun = runRepository.allRuns.value.firstOrNull { it.id == currentRunId }

            if (currentRun != null && currentRun.badges < snapshot.badgeCount) {
                val team = snapshot.party.map { mon ->
                    PokemonTeamMember(
                        name = mon.nickname,
                        species = mon.species.lowercase(),
                        level = mon.level,
                        iconUrl = ""
                    )
                }
                runRepository.saveBadgeSnapshot(
                    runId = currentRunId,
                    badgeNumber = snapshot.badgeCount,
                    team = team,
                    deaths = snapshot.deaths.size
                )
                println("Badge ${snapshot.badgeCount} snapshot saved")
            }
        } else {
            val currentBadges = runRepository.allRuns.value
                .firstOrNull { it.id == currentRunId }?.badges ?: 0
            runRepository.updateRunStats(
                runId = currentRunId,
                badges = maxOf(snapshot.badgeCount, currentBadges),
                deaths = snapshot.deaths.size
            )
        }

        snapshot.deaths.forEach { death ->
            println("${death.nickname} (${death.species}) Lv${death.level}")
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

    private fun startGbaWatcher() {
        gbaWatcher?.stop()
        gbaWatcher = GbaFileWatcher(
            FilePathProvider.getRomsDirectory().absolutePath
        ) { changedFile ->
            println("Emulator wrote save: ${changedFile.name}")
            val runFolder = activeRunFolder ?: return@GbaFileWatcher
            GbaRunManager.backupActiveSave(runFolder)
                .onFailure { println("Backup failed: ${it.message}") }
        }
        gbaWatcher?.start(viewModelScope)
    }

    private fun checkExistingRom() {
        val rom = FilePathProvider.getActiveRomFile()
        if (rom != null) {
            println("🎮 ROM ready: ${rom.name}")

            var foundFolder: File? = null
            var foundGame: GameVersion? = null

            for (game in GameVersion.entries) {
                if (!FilePathProvider.isGameVerified(game)) continue
                val folders = GbaRunManager.discoverRunFolders(game)
                if (folders.isEmpty()) continue
                foundFolder = folders.first()
                foundGame = game
                break
            }

            if (foundFolder != null && foundGame != null) {
                val runIdFile = File(foundFolder, "runid.txt")
                println("🔍 Auto-selected: ${foundGame.name}/${foundFolder.name}")
                if (runIdFile.exists()) {
                    val runId = runIdFile.readText().trim()
                    val run = runRepository.allRuns.value.firstOrNull { it.id == runId }
                    if (run != null) {
                        runRepository.setActiveRun(runId)
                    }
                }
                _uiState.update { it.copy(
                    romLoaded = true,
                    romName = rom.nameWithoutExtension,
                    activeGame = foundGame,
                    activeGbaRun = foundFolder.name
                ) }
            } else {
                _uiState.update { it.copy(
                    romLoaded = true,
                    romName = rom.nameWithoutExtension
                ) }
            }

            loadVerifiedGames()
            startGbaWatcher()
        }
    }

    fun switchGbaRun(gameVersion: GameVersion, targetFolderName: String) {
        val target = FilePathProvider.getRunFolder(gameVersion, targetFolderName)
        if (!target.exists()) {
            println(" Target folder does not exist")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            isSwitching = true
            _uiState.update { it.copy(isSwitchingRun = true, activeGame = gameVersion) }

            GbaRunManager.switchRun(activeRunFolder, target)
                .onSuccess {
                    _uiState.update { it.copy(
                        romLoaded = true,
                        activeGame = gameVersion,
                        activeGbaRun = targetFolderName
                    ) }


                    val runIdFile = File(target, "runid.txt")
                    if (runIdFile.exists()) {
                        val runId = runIdFile.readText().trim()
                        withContext(Dispatchers.Main) {
                            runRepository.setActiveRun(runId)
                            _uiState.update { it.copy(
                                activeGbaRun = targetFolderName,
                                activeGame = gameVersion
                            ) }
                            if (FilePathProvider.getActiveSavFile() == null) {
                                _uiState.update { it.copy(
                                    trainerName = "New Run",
                                    badges = "0/8",
                                    deaths = "0",
                                    pokemonTeamIcons = emptyList()
                                ) }
                            }
                        }
                    } else{
                        withContext(Dispatchers.Main) {
                            _uiState.update { it.copy(
                                activeGbaRun = targetFolderName,
                                activeGame = gameVersion,
                                trainerName = "New Run",
                                badges = "0/8",
                                deaths = "0",
                                pokemonTeamIcons = emptyList()
                            ) }
                        }
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(gbaError = error.message) }
                }

            delay(1000)
            isSwitching = false
            _uiState.update { it.copy(isSwitchingRun = false) }
        }
    }

    fun createGbaRun(gameVersion: GameVersion, runName: String) {
        println("🆕 createGbaRun called: $gameVersion $runName")
        viewModelScope.launch(Dispatchers.IO) {

            // Create the folder first
            GbaRunManager.createRunFolder(gameVersion, runName)
                .onSuccess { folder ->
                    // Use a placeholder save path - mGBA will create the actual save
                    val savePath = File(
                        FilePathProvider.getRomsDirectory(),
                        FilePathProvider.getActiveRomFile()?.nameWithoutExtension + ".sav"
                    ).absolutePath

                    runRepository.createRun(savePath, gameVersion)
                        .onSuccess { newRun ->
                            val runId = newRun.id

                            val previousFolder = activeRunFolder
                            if (previousFolder != null) {
                                GbaRunManager.backupActiveSave(previousFolder)
                            }

                            FilePathProvider.getActiveSavFile()?.delete()

                            File(folder, "runid.txt").writeText(runId)

                            loadVerifiedGames()
                            withContext(Dispatchers.Main) {
                                switchGbaRun(gameVersion, runName)
                            }
                        }
                        .onFailure { error ->
                            error.printStackTrace()
                            _uiState.update { it.copy(gbaError = error.message) }
                        }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(gbaError = error.message) }
                }
        }
    }




    fun deleteGbaRun(gameVersion: GameVersion, runName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val userId = UserSession.currentUserId.value ?: return@launch
            if (userId != null) {
                SupabaseClient.applyEloDelta(userId, -50)
                withContext(Dispatchers.Main) {
                    loadPlayerRating()
                }

            }

            GbaRunManager.deleteRunFolder(runName, gameVersion)
                .onSuccess {
                    if (activeRunFolder?.name == runName) {
                        _uiState.update { it.copy(
                            activeGbaRun = null,
                            activeGame = null,
                            romLoaded = true,
                            trainerName = "No run selected",
                            badges = "0/8",
                            deaths = "0",
                            pokemonTeamIcons = emptyList(),
                            currentRunId = null
                        ) }
                    } else {
                        _uiState.update { it.copy(
                            activeGbaRun = if (uiState.value.activeGbaRun == runName) null else uiState.value.activeGbaRun
                        ) }
                    }
                    loadVerifiedGames()
                }
                .onFailure { error ->
                    _uiState.update { it.copy(gbaError = error.message) }


                }

        }
    }

    private fun loadVerifiedGames() {
        val verified = GameVersion.entries.filter {
            FilePathProvider.isGameVerified(it)
        }.toSet()

        val slots = GameVersion.entries.associate { game ->
            game to GbaRunManager.discoverRunFolders(game).map { it.name }
        }
        _uiState.update { it.copy(
            verifiedGames = verified,
            slotsByGame = slots
        ) }
    }

    fun verifyRom(gameVersion: GameVersion, sourceFile: File) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, gbaError = null) }

            val validation = GbaValidator.validate(sourceFile)

            if (!validation.isValid || validation.gameVersion != gameVersion) {
                _uiState.update { it.copy(
                    isLoading = false,
                    gbaError = "Invalid ROM: ${validation.errorMessage ?: "Wrong game version"}"
                ) }
                return@launch
            }

            FilePathProvider.markGameVerified(gameVersion)
            loadVerifiedGames()
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun switchGame(gameVersion: GameVersion) {
        _uiState.update { it.copy(activeGame = gameVersion) }
    }


    fun findCasualMatch() {
        viewModelScope.launch {
            val showdownName = uiState.value.showdownUsername.trim()
            if (showdownName.isBlank()) {
                _uiState.update { it.copy(gbaError = "Enter your Showdown username first") }
                return@launch
            }

            val currentRun = runRepository.currentRun.value
            if (currentRun == null) return@launch

            val latestSnapshot = currentRun.badgeSnapshots.lastOrNull()
            if (latestSnapshot == null) {
                _uiState.update { it.copy(gbaError = "No badge snapshot found. Beat a gym first!") }
                return@launch
            }

            _uiState.update { it.copy(isLoading = true) }

            try {
                SupabaseClient.queueForMatch(
                    badgeCount = latestSnapshot.badgeNumber,
                    team = latestSnapshot.team,
                    deaths = latestSnapshot.deaths
                )

                val opponent = SupabaseClient.findMatch(latestSnapshot.badgeNumber)
                if (opponent == null) {
                    _uiState.update { it.copy(
                        isLoading = false,
                        gbaError = "No opponents found."
                    ) }
                    return@launch
                }

                val match = SupabaseClient.createMatch(
                    opponent = opponent,
                    myTeam = latestSnapshot.team,
                    myDeaths = latestSnapshot.deaths,
                    badgeCount = latestSnapshot.badgeNumber
                )

                if (match != null) {
                    currentMatch = match
                    _uiState.update { it.copy(
                        isLoading = false,
                        currentMatch = match,
                        showMatchDialog = true
                    ) }

                    // Open Showdown in the browser so the user can log in and play
                    BrowserLauncher.openUrl("http://localhost:8000/")

                    // Start the bot — it will challenge the user's Showdown username
                    startShowdownMatch(match)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(
                    isLoading = false,
                    gbaError = "Matchmaking error: ${e.message}"
                ) }
            }
        }
    }



    fun reportWin() {
        viewModelScope.launch {
            val match = currentMatch ?: return@launch
            match.id ?: return@launch
            SupabaseClient.reportMatchResult(match.id, won = true)
            _uiState.update { it.copy(showMatchDialog = false, currentMatch = null) }
            currentMatch = null
        }
    }

    fun reportLoss() {
        viewModelScope.launch {
            val match = currentMatch ?: return@launch
            match.id ?: return@launch
            SupabaseClient.reportMatchResult(match.id, won = false)
            _uiState.update { it.copy(showMatchDialog = false, currentMatch = null) }
            currentMatch = null
        }
    }


    fun startShowdownMatch(match: Match) {
        showdownJob?.cancel()
        showdownJob = viewModelScope.launch {
            val playerUsername = uiState.value.showdownUsername.trim()
            if (playerUsername.isBlank()) {
                _uiState.update { it.copy(gbaError = "No Showdown username set") }
                return@launch
            }
            val botUsername = "DualockeBot${System.currentTimeMillis() % 1000}"

            val myTeam = Json.decodeFromJsonElement<List<PokemonTeamMember>>(match.player1_team)
            val opponentTeam = Json.decodeFromJsonElement<List<PokemonTeamMember>>(match.player2_team)

            showdownClient.startMatch(
                playerUsername = playerUsername,
                botUsername = botUsername,
                playerTeam = myTeam,
                botTeam = opponentTeam,
                onMatchStarted = { roomId ->
                    println("[SHOWDOWN] Match started in room: $roomId")
                    _uiState.update { it.copy(showdownRoomId = roomId) }

                    // Optional: open the specific battle URL too, in case they're not already there
                    BrowserLauncher.openUrl("http://localhost:8000/$roomId")
                },
                onWin = { winner ->
                    val playerWon = winner.equals(playerUsername, ignoreCase = true)
                    println("[SHOWDOWN] Match ended. Player won: $playerWon")
                    viewModelScope.launch {
                        match.id?.let { matchId ->
                            SupabaseClient.reportMatchResult(matchId, playerWon)
                        }
                        loadPlayerRating()
                        _uiState.update { it.copy(
                            showMatchResult = true,
                            playerWon = playerWon,
                            showMatchDialog = false
                        ) }
                    }
                },
                onError = { error ->
                    _uiState.update { it.copy(gbaError = "Showdown error: $error") }
                }
            )
        }
    }

    fun dismissMatchResult() {
        _uiState.update { it.copy(
            showMatchResult = false,
            playerWon = null,
            showdownRoomId = null,
            showdownUrl = null,
            currentMatch = null,
            showMatchDialog = false
        ) }
        currentMatch = null
    }

    private fun loadPlayerRating() {
        viewModelScope.launch {
            val userId = UserSession.currentUserId.value ?: return@launch
            val rating = SupabaseClient.getPlayerRating(userId)
            println(" Loaded rating: elo=${rating?.elo} rank=${getRankFromElo(rating?.elo ?: 1000)}")

            _uiState.update { it.copy(
                elo = rating?.elo ?: 1000,
                rank = getRankFromElo(rating?.elo ?: 1000)
            ) }

            println(" Updated uiState elo: ${uiState.value.elo}")

        }
    }

    private fun clearDeathTracking() {
        println("🧹 Clearing death tracking state")
        processedDeaths.clear()
        deathTimestamps.clear()
        baselineDeathCount = null
    }

    fun findRankedMatch() {
        // TODO: Implement ranked match finding
        println("Finding ranked match...")
    }

    fun viewHistory() {
        // TODO: Navigate to match history
        println("Viewing history...")
    }

    fun viewCommunity() {
    }

    fun loadLeaderboard() {
        viewModelScope.launch {
            val entries = SupabaseClient.getLeaderboard()
            _uiState.update { it.copy(leaderboard = entries) }
        }
    }

    fun toggleLeaderboard() {
        if (!uiState.value.showLeaderboard) loadLeaderboard()
        _uiState.update { it.copy(showLeaderboard = !it.showLeaderboard) }
    }

    fun openSettings() {
        // TODO: Navigate to settings
        println("Opening settings...")
    }

    override fun onCleared() {
        super.onCleared()
        pokeApiClient.close()
        showdownClient.disconnect()
        tcpClient.disconnect()
    }
}