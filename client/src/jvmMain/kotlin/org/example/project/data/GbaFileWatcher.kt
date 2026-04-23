package org.example.project.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchService
import java.util.concurrent.TimeUnit

class GbaFileWatcher(
    private val gbaRootPath: String,
    private val onSaveChanged: (changedFile: File) -> Unit
) {
    private var watchJob: Job? = null
    private val watchService: WatchService = FileSystems.getDefault().newWatchService()

    fun start(scope: CoroutineScope) {
        val saveDir = File(gbaRootPath)
        if (!saveDir.exists()) return

        saveDir.toPath().register(
            watchService,
            StandardWatchEventKinds.ENTRY_MODIFY
        )

        watchJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val key = watchService.poll(500, TimeUnit.MILLISECONDS) ?: continue
                for (event in key.pollEvents()) {
                    val filename = event.context().toString()
                    if (filename.endsWith(".sav")) {
                        onSaveChanged(File(gbaRootPath, filename))
                    }
                }
                key.reset()
            }
        }
    }

    fun stop() {
        watchJob?.cancel()
        watchService.close()
    }
}