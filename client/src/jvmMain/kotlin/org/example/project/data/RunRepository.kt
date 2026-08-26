package org.example.project.data

import androidx.compose.runtime.MutableState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import org.example.project.data.FilePathProvider.getRomsDirectory
import org.example.project.domain.models.BadgeSnapshot
import org.example.project.domain.models.GameVersion
import org.example.project.domain.models.PokemonRun
import org.example.project.domain.models.PokemonTeamMember
import java.io.File
import java.util.UUID

object RunRepository {
    private val _currentRun = MutableStateFlow<PokemonRun?>(null)
    val currentRun: StateFlow<PokemonRun?> = _currentRun.asStateFlow()
    private val _allRuns = MutableStateFlow<List<PokemonRun>>(emptyList())
    val allRuns: StateFlow<List<PokemonRun>> = _allRuns.asStateFlow()

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun initialize() {
        loadRuns()
    }

    private fun loadRuns() {
        try {
            val path = FilePathProvider.getRunsConfigPath()
            val configFile = File(path)
            println("Loading runs from: $path")
            if (configFile.exists()) {
                val jsonString = configFile.readText()
                val runs = json.decodeFromString<List<PokemonRun>>(jsonString)
                _allRuns.value = runs
                _currentRun.value = runs.firstOrNull { it.isActive }
                println("Loaded ${runs.size} runs")
            } else {
                _allRuns.value = emptyList()
                _currentRun.value = null
                println("No runs file yet for this user")
            }
        } catch (e: Exception) {
            println("Error loading runs: ${e.message}")
            _allRuns.value = emptyList()
            _currentRun.value = null
        }
    }

    private fun saveRuns() {
        try {
            val configFile = File(FilePathProvider.getRunsConfigPath())
            configFile.parentFile?.mkdirs()
            val jsonString = json.encodeToString(_allRuns.value)
            configFile.writeText(jsonString)
        } catch (e: Exception) {
            println("Error saving runs: ${e.message}")
        }
    }

    fun createRun(
        saveFilePath: String,
        gameVersion: GameVersion = GameVersion.FIRE_RED
    ): Result<PokemonRun> {
        return try {

            val saveFile = File(saveFilePath)

            val trainerName = if (saveFile.exists() && saveFile.length() > 1000) {
                try {
                    val saveData = SaveFileManager.readSaveFile(saveFilePath)
                    val saveBase = SaveFileManager.getActiveFireRedSaveBase(saveData)
                    if (saveBase < 0) {
                        "Unknown"
                    } else {
                        val trainerSection = SaveFileManager.findSection(saveData, saveBase, 0)
                        val nameBytes = saveData.copyOfRange(trainerSection, trainerSection + 7)
                        FireRedTextDecoder.decodeFRString(nameBytes)
                    }
                } catch (e: Exception) {
                    println("Could not read trainer name: ${e.message}")
                    "Unknown"
                }
            } else {
                "Unknown"
            }

            val run = PokemonRun(
                id = UUID.randomUUID().toString(),
                trainerName = trainerName,
                gameName = gameVersion.getDisplayName(),
                gameVersion = gameVersion,
                saveFilePath = saveFilePath,
                isActive = _allRuns.value.isEmpty()
            )

            val updatedRuns = _allRuns.value + run
            _allRuns.value = updatedRuns

            if (run.isActive) {
                _currentRun.value = run
            }
            saveRuns()
            Result.success(run)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }



    fun createRunFromRom(
        romFileName: String,
        gameVersion: GameVersion = GameVersion.FIRE_RED
    ): Result<PokemonRun> {
        val romPath = FilePathProvider.getRomPath(romFileName)
        val savePath = FilePathProvider.getRomPath(  // point to roms directory
            romFileName.replaceAfterLast('.', "sav")
        )

        if (!File(romPath).exists()) {
            return Result.failure(Exception("ROM file not found: $romPath"))
        }

        // Don't create a save file here, mGBA will create it next to the ROM
        return createRun(savePath, gameVersion)
    }



    fun importRom(
        sourceFile: File,
        gameVersion: GameVersion = GameVersion.FIRE_RED
    ): Result<PokemonRun>{
        return try {
            val importResult = FilePathProvider.importRom(sourceFile)
            if (importResult.isFailure){
                return Result.failure(importResult.exceptionOrNull() ?: Exception("Unknown import error"))
            }
            val saveFilename = sourceFile.nameWithoutExtension + ".sav"
            val savePath = FilePathProvider.getSavePath(saveFilename)
            val saveFile = File(savePath)

            if (!saveFile.exists()) {
                val sourceSave = File(sourceFile.parent,saveFilename)
                if (sourceSave.exists()){
                    FilePathProvider.importSave(sourceSave)
                }else{
                    saveFile.createNewFile()
                }
            }

            createRunFromRom(sourceFile.name,gameVersion)

        } catch (e: Exception){
            Result.failure(e)
        }
    }

    suspend fun loadRunData(run: PokemonRun): Result<RunData>{
        return try {

            val activeSav = FilePathProvider.getActiveSavFile()

            if (activeSav == null || !activeSav.exists() || activeSav.length() < 1000) {
                return Result.success(RunData(
                    trainerName = "Unknown",
                    teamMembers = emptyList(),
                    rawTeamData = ByteArray(0)
                ))
            }

            val saveData = SaveFileManager.readSaveFile(activeSav.absolutePath)
            val saveBase = SaveFileManager.getActiveFireRedSaveBase(saveData)

            val trainerSection = SaveFileManager.findSection(saveData,saveBase,0)
            val teamItemSection = SaveFileManager.findSection(saveData,saveBase,1)

            val nameBytes = saveData.copyOfRange(trainerSection, trainerSection + 7)
            val trainerName = FireRedTextDecoder.decodeFRString(nameBytes)

            val teamSizeBytes = saveData.copyOfRange(
                teamItemSection + 0x0034,
                teamItemSection + 0x0035
            )
            val teamSize = teamSizeBytes[0].toInt() and 0xFF

            val teamBytes = saveData.copyOfRange(
                teamItemSection + 0x0038,
                teamItemSection + 0x0038 + 600
            )

            val teamMembers = mutableListOf<PokemonTeamMember>()
            for (i in 0 until teamSize) {
                val start = i * 100
                val end = start + 100
                val pokemonData = teamBytes.copyOfRange(start, end)

                val pokemonBytesName = pokemonData.copyOfRange(0x08, 0x08 + 10)
                val nickname = FireRedTextDecoder.decodeFRString(pokemonBytesName)

                teamMembers.add(
                    PokemonTeamMember(
                        name = nickname.trim(),
                        species = nickname.trim(),
                        level = pokemonData[0x54].toInt() and 0xFF,
                        iconUrl = ""
                    )
                )
            }

            Result.success(
                RunData(
                    trainerName = trainerName,
                    teamMembers = teamMembers,
                    rawTeamData = teamBytes
                )
            )
        } catch (e: Exception){
            Result.failure(e)
        }
    }

    fun setActiveRun(runId: String){
        val updatedRuns = _allRuns.value.map { run ->
            run.copy(
                isActive = run.id == runId,
                lastPlayedAt = if (run.id == runId) System.currentTimeMillis() else run.lastPlayedAt
            )
        }

        _allRuns.value = updatedRuns
        _currentRun.value = updatedRuns.firstOrNull{it.isActive}
        saveRuns()
    }

    fun deleteRun(runId: String,deleteFiles: Boolean=false){
        val runToDelete = _allRuns.value.firstOrNull{it.id == runId}
        val updatedRuns = _allRuns.value.filter { it.id != runId}

        if (deleteFiles && runToDelete != null){
            try {
                File(runToDelete.saveFilePath).delete()
            } catch (e: Exception){
                println("Error deleting files ${e.message}")
            }
        }

        val finalRuns = if (runToDelete?.isActive == true && updatedRuns.isNotEmpty()){
            updatedRuns.mapIndexed { index, run ->
                if (index == 0){
                    run.copy(isActive = true)
                } else run
            }
        } else{
            updatedRuns
        }

        _allRuns.value = finalRuns
        _currentRun.value = finalRuns.firstOrNull{it.isActive}
        saveRuns()
    }



    fun updateRunStats(
        runId: String,
        badges: Int?,
        deaths: Int? = null
    ){
        val updatedRuns = _allRuns.value.map { run ->
            if (run.id == runId){
                run.copy(
                    badges = badges ?: run.badges,
                    deaths = deaths ?: run.deaths
                )
            }else{
                run
            }
        }

        _allRuns.value = updatedRuns
        if (_currentRun.value?.id == runId){
            _currentRun.value = updatedRuns.first{it.id == runId}
        }
        saveRuns()
    }

    fun updateRunTeam(
        runId: String,
        team: List<PokemonTeamMember>
    ){
        val updatedRuns = _allRuns.value.map { run ->
            if (run.id == runId) {
                run.copy(pokemonTeam = team)
            }else{
                run
            }
        }

        _allRuns.value = updatedRuns
        if (_currentRun.value?.id  == runId){
            _currentRun.value = updatedRuns.first{it.id == runId}
        }
        saveRuns()
    }

    fun saveBadgeSnapshot(runId: String, badgeNumber: Int, team: List<PokemonTeamMember>, deaths: Int) {
        val updatedRuns = _allRuns.value.map { run ->
            if (run.id == runId) {
                val alreadyExists = run.badgeSnapshots.any { it.badgeNumber == badgeNumber }
                if (!alreadyExists) {
                    println("Saving badge $badgeNumber snapshot: ${team.size} pokemon, $deaths deaths")
                    run.copy(
                        badges = badgeNumber,
                        badgeSnapshots = run.badgeSnapshots + BadgeSnapshot(
                            badgeNumber = badgeNumber,
                            team = team,
                            deaths = deaths
                        )
                    )
                } else {
                    println("Badge $badgeNumber snapshot already exists, skipping")
                    run
                }
            } else run
        }
        _allRuns.value = updatedRuns
        _currentRun.value = updatedRuns.firstOrNull { it.isActive }
        saveRuns()
    }

    fun reloadForUser() {
        println("reloadForUser called")
        loadRuns()
    }

}

data class RunData(
    val trainerName: String,
    val teamMembers: List<PokemonTeamMember>,
    val rawTeamData: ByteArray
)

