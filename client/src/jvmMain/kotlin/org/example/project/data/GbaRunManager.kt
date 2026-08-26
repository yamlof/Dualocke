package org.example.project.data

import org.example.project.domain.models.GameVersion
import java.io.File

object GbaRunManager {

    fun getSavInRunFolder(runFolder: File): File? {
        return runFolder.listFiles()?.firstOrNull { it.extension.lowercase() == "sav" }
    }

    fun backupActiveSave(runFolder: File): Result<Unit> {
        return try {
            val activeSav = FilePathProvider.getActiveSavFile()
                ?: return Result.failure(Exception("No active .sav found"))
            activeSav.copyTo(File(runFolder, activeSav.name), overwrite = true)
            println("Backed up to ${runFolder.name}/")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun loadRunSave(runFolder: File): Result<Unit> {
        return try {
            val runSav = getSavInRunFolder(runFolder)
            if (runSav == null) {
                println("No save in folder yet: ${runFolder.name}")
                return Result.success(Unit)
            }
            val activeSavPath = File(FilePathProvider.getRomsDirectory(), runSav.name)
            runSav.copyTo(activeSavPath, overwrite = true)
            println("Loaded save from ${runFolder.name}/")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun switchRun(currentRunFolder: File?, targetRunFolder: File): Result<Unit> {
        if (currentRunFolder != null) {
            backupActiveSave(currentRunFolder)
                .onFailure {
                    println("Backup skipped: ${it.message}")
                }
        }
        return loadRunSave(targetRunFolder)
    }

    fun deleteRunFolder(runName: String, gameVersion: GameVersion): Result<Unit> {
        return try {
            val runFolder = FilePathProvider.getRunFolder(gameVersion, runName)
            if (!runFolder.exists())
                return Result.failure(Exception("Run '$runName' not found"))
            runFolder.deleteRecursively()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun discoverRunFolders(gameVersion: GameVersion): List<File> {
        return FilePathProvider.getRunsDirectory(gameVersion)
            .listFiles()
            ?.filter { it.isDirectory }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    fun createRunFolder(gameVersion: GameVersion, runName: String): Result<File> {
        return try {
            val runFolder = FilePathProvider.getRunFolder(gameVersion, runName)
            if (runFolder.exists())
                return Result.failure(Exception("Run '$runName' already exists"))
            runFolder.mkdirs()
            Result.success(runFolder)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}