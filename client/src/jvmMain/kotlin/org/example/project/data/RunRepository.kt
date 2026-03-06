package org.example.project.data

import androidx.compose.runtime.MutableState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import org.example.project.data.FilePathProvider.getRomsDirectory
import org.example.project.domain.models.GameVersion
import org.example.project.domain.models.PokemonRun
import org.example.project.domain.models.PokemonTeamMember
import java.io.File
import java.util.UUID

class RunRepository (
    private val runsConfigPath: String = FilePathProvider.getRunsConfigPath()
){
    private val _currentRun = MutableStateFlow<PokemonRun?>(null)
    val currentRun : StateFlow<PokemonRun?> = _currentRun.asStateFlow()

    private val _allRuns = MutableStateFlow<List<PokemonRun>>(emptyList())
    val allRuns : StateFlow<List<PokemonRun>> = _allRuns.asStateFlow()

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    init {
        loadRuns()
    }

    private fun loadRuns(){
        try {
            val configFile = File(runsConfigPath)
            if (configFile.exists()){
                val jsonString = configFile.readText()
                val runs = json.decodeFromString<List<PokemonRun>>(jsonString)
                _allRuns.value = runs
                _currentRun.value = runs.firstOrNull {it.isActive}!!
            }
        } catch (e: Exception) {
            println("Error loading runs: ${e.message}")
            _allRuns.value = emptyList()
        }
    }

    private fun saveRuns(){
        try {
            val configFile = File(runsConfigPath)
            configFile.parentFile?.mkdirs()
            val jsonString = json.encodeToString(_allRuns.value)
            configFile.writeText(jsonString)
        }catch (e: Exception){
            println("Error saving runs: ${e.message}")
        }
    }

    fun createRun(
        saveFilePath: String,
        gameVersion: GameVersion = GameVersion.FIRE_RED
    ): Result<PokemonRun>{
        return try {
            val saveData = SaveFileManager.readSaveFile(saveFilePath)
            val saveBase = SaveFileManager.getActiveFireRedSaveBase(saveData)

            val trainerSection = SaveFileManager.findSection(saveData,saveBase,0)
            val nameBytes = saveData.copyOfRange(trainerSection,trainerSection+7)
            val trainerName = FireRedTextDecoder.decodeFRString(nameBytes)

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
        }catch (e: Exception){
            Result.failure(e)
        }
    }

    fun createRunFromRom(
        romFileName:String,
        gameVersion: GameVersion = GameVersion.FIRE_RED
    ): Result<PokemonRun>{
        val romPath = FilePathProvider.getRomPath(romFileName)
        val saveFileName = romFileName.replaceAfterLast('.',"sav")
        val savePath = FilePathProvider.getSavePath(saveFileName)

        if (!File(romPath).exists()){
            return Result.failure(Exception("ROM file not found: $romPath"))
        }

        val saveFile = File(savePath)
        if (!saveFile.exists()){
            saveFile.createNewFile()
            println("Created a new save file: $savePath")
        }

        return createRun(savePath,gameVersion)
    }

    fun importRom(
        sourceFile: File,
        gameVersion: GameVersion = GameVersion.FIRE_RED
    ): Result<PokemonRun>{
        return try {
            val importResult = FilePathProvider.importRom(sourceFile)
            if (importResult.isFailure){
                return Result.failure(importResult.exceptionOrNull()!!)
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
            val saveData = SaveFileManager.readSaveFile(run.saveFilePath)
            val saveBase = SaveFileManager.getActiveFireRedSaveBase(saveData)

            val trainerSection = SaveFileManager.findSection(saveData,saveBase,0)
            val teamItemSection = SaveFileManager.findSection(saveData,saveBase,1)

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

                // You can extract more data here (level, species, etc.)
                teamMembers.add(
                    PokemonTeamMember(
                        name = nickname.trim(),
                        species = nickname.trim(), // TODO: Get actual species
                        level = 1, // TODO: Extract level from save
                        iconUrl = "" // Will be loaded separately
                    )
                )
            }

            Result.success(
                RunData(
                    trainerName = run.trainerName,
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
        _currentRun.value = updatedRuns.firstOrNull{it.isActive}!!
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

    /**
     * Push our save file to the emulator's expected location.
     * Call this when the user is about to play this run.
     */
    fun syncRunToEmulator(runId: String): Result<Unit> {
        val run = _allRuns.value.firstOrNull { it.id == runId }
            ?: return Result.failure(Exception("Run not found"))
        val emulatorSavePath = run.emulatorSavePath
            ?: return Result.failure(Exception("No emulator save path set for this run"))

        return FilePathProvider.syncSaveToEmulator(run.saveFilePath, emulatorSavePath)
    }

    /**
     * Pull the emulator's save back into our managed storage.
     * Call this after the user finishes playing (before switching runs).
     */
    fun syncRunFromEmulator(runId: String): Result<Unit> {
        val run = _allRuns.value.firstOrNull { it.id == runId }
            ?: return Result.failure(Exception("Run not found"))
        val emulatorSavePath = run.emulatorSavePath
            ?: return Result.failure(Exception("No emulator save path set for this run"))

        return FilePathProvider.syncSaveFromEmulator(emulatorSavePath, run.saveFilePath)
    }

    /**
     * Set where this run's save should go when syncing to the emulator.
     * The user picks this path once via a file chooser dialog.
     */
    fun setEmulatorPaths(
        runId: String,
        emulatorSavePath: String?,
        emulatorRomPath: String? = null
    ) {
        val updatedRuns = _allRuns.value.map { run ->
            if (run.id == runId) {
                run.copy(
                    emulatorSavePath = emulatorSavePath,
                    emulatorRomPath = emulatorRomPath
                )
            } else run
        }
        _allRuns.value = updatedRuns
        if (_currentRun.value?.id == runId) {
            _currentRun.value = updatedRuns.first { it.id == runId }
        }
        saveRuns()
    }

    /**
     * Convenience: sync current active run to emulator.
     */
    fun syncActiveRunToEmulator(): Result<Unit> {
        val current = _currentRun.value
            ?: return Result.failure(Exception("No active run"))
        return syncRunToEmulator(current.id)
    }

    fun syncActiveRunFromEmulator(): Result<Unit> {
        val current = _currentRun.value
            ?: return Result.failure(Exception("No active run"))
        return syncRunFromEmulator(current.id)
    }



}

data class RunData(
    val trainerName: String,
    val teamMembers: List<PokemonTeamMember>,
    val rawTeamData: ByteArray
)