package org.example.project.data

import org.example.project.domain.models.GameVersion
import java.io.File
import java.nio.file.Paths

object FilePathProvider {
    private const val APP_NAME = "Dualocke"

    fun getAppDataDirectory(): File{
        val userHome = System.getProperty("user.home")
        val osName = System.getProperty("os.name").lowercase()

        val appDataPath = when {
            osName.contains("mac") -> {
                Paths.get(userHome,"Library","Application Support", APP_NAME).toString()
            }
            else -> {
                val xdgDataHome = System.getenv("XDG_DATA_HOME")
                    ?: "$userHome/.local/share"
                Paths.get(xdgDataHome,APP_NAME).toString()
            }
        }

        val directory = File(appDataPath)
        if (!directory.exists()){
            directory.mkdirs()
        }
        return directory
    }

    fun getDataDirectory(): File{
        val dataDir = File(getAppDataDirectory(),"data")
        if (!dataDir.exists()){
            dataDir.mkdirs()
        }
        return dataDir
    }


    fun getRomsDirectory(): File{
        val romsDir = File(getDataDirectory(),"roms")
        if (!romsDir.exists()){
            romsDir.mkdirs()
        }
        return romsDir
    }

    fun getSavesDirectory(): File{
        val saveDir = File(getAppDataDirectory(),"saves")
        if (!saveDir.exists()){
            saveDir.mkdirs()
        }
        return saveDir
    }

    fun getRunsConfigPath(): String {
        val userDir = File(getDataDirectory(), "users/${UserSession.activeUserId()}")
        userDir.mkdirs()
        return File(userDir, "runs.json").absolutePath
    }

    fun getRunsDirectory(gameVersion: GameVersion): File {
        val runsDir = File(
            getSavesDirectory(),
            "users/${UserSession.activeUserId()}/runs/${gameVersion.name}"
        )
        runsDir.mkdirs()
        return runsDir
    }

    fun getRunFolder(gameVersion: GameVersion, runName: String): File {
        return File(getRunsDirectory(gameVersion), runName)
    }

    fun getActiveRomFile(): File? {
        return getRomsDirectory().listFiles()
            ?.firstOrNull { it.extension.lowercase() == "gba" }
    }

    fun getActiveSavFile(): File? {
        return getRomsDirectory().listFiles()
            ?.firstOrNull { it.extension.lowercase() == "sav" }
    }

    fun getRomPath(filename: String): String{
        return File(getRomsDirectory(),filename).absolutePath
    }

    fun getSavePath(filename: String): String{
        return File(getSavesDirectory(),filename).absolutePath
    }

    fun importRom(sourcefile: File): Result<File>{
        return try {
            val destFile = File(getRomsDirectory(),sourcefile.name)
            sourcefile.copyTo(destFile, overwrite = true)
            Result.success(destFile)
        }catch (e: Exception){
            Result.failure(e)
        }
    }
    fun importSave(sourcefile: File): Result<File>{
        return try {
            val destFile = File(getSavesDirectory(),sourcefile.name)
            sourcefile.copyTo(destFile, overwrite = false)
            Result.success(destFile)
        }catch (e: Exception){
            Result.failure(e)
        }
    }

    fun getBundledRomPath(gameVersion: GameVersion): File {
        val os = System.getProperty("os.name").lowercase()
        val fileName = when (gameVersion) {
            GameVersion.FIRE_RED -> "firered.gba"
            GameVersion.LEAF_GREEN -> "leafgreen.gba"
            GameVersion.EMERALD -> "emerald.gba"
            GameVersion.RUBY -> "ruby.gba"
            GameVersion.SAPPHIRE -> "sapphire.gba"
        }
        return File("resources/roms", fileName)
    }

    fun isGameVerified(gameVersion: GameVersion): Boolean {
        val verifiedFile = File(getDataDirectory(), "verified/${gameVersion.name}.verified")
        return verifiedFile.exists()
    }

    fun markGameVerified(gameVersion: GameVersion) {
        val verifiedDir = File(getDataDirectory(), "verified")
        verifiedDir.mkdirs()
        File(verifiedDir, "${gameVersion.name}.verified").createNewFile()
    }

    fun getRomVerificationPath(gameVersion: GameVersion): String {
        val verifiedDir = File(getDataDirectory(), "verified")
        verifiedDir.mkdirs()
        return File(verifiedDir, "${gameVersion.name}.verified").absolutePath
    }



}