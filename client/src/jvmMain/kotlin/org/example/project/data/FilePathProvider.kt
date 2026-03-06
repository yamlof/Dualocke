package org.example.project.data

import androidx.compose.ui.platform.isDebugInspectorInfoEnabled
import java.io.File
import java.nio.file.Paths

object FilePathProvider {

    private const val APP_NAME = "Dualocke"
    private const val EMULATOR_PATHS_KEY = "emulator_paths.json"

    fun getEmulatorPathsFile() : File{
        return File(getDataDirectory(),EMULATOR_PATHS_KEY)
    }

    fun syncSaveToEmulator(ourSavePath: String, emulatorSavePath: String): Result<Unit> {
        return try {
            val source = File(ourSavePath)
            val dest = File(emulatorSavePath)
            if (!source.exists()) return Result.failure(Exception("Save not found: $ourSavePath"))
            dest.parentFile?.mkdirs()
            source.copyTo(dest, overwrite = true)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun syncSaveFromEmulator(emulatorSavePath: String, ourSavePath: String): Result<Unit> {
        return try {
            val source = File(emulatorSavePath)
            val dest = File(ourSavePath)
            if (!source.exists()) return Result.failure(Exception("Emulator save not found: $emulatorSavePath"))
            dest.parentFile?.mkdirs()
            source.copyTo(dest, overwrite = true)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }



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

    fun getRunsConfigPath(): String{
        return File(getDataDirectory(), "runs.json").absolutePath
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

    fun getSaveSize(file: File): String{
        val bytes = file.length()
        return when{
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes/1024} KB"
            else -> "${bytes/(1024*1024)} MB"
        }
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
            sourcefile.copyTo(destFile, overwrite = false)
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

}