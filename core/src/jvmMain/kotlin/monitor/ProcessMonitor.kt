package monitor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds

actual class ProcessMonitor {
    private var monitoringJob: Job? = null
    private var lastState = false

    actual fun isMgbaRunning(): Boolean{
        return ProcessHandle.allProcesses()
            .anyMatch { process ->
                val command = process.info().command().orElse("")
                command.contains("mGBA", ignoreCase = true) || command.endsWith("mgba")
            }
    }

    actual fun startMonitoring(onStateChange:(Boolean)-> Unit){
        monitoringJob?.cancel()

        monitoringJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                val currentState = isMgbaRunning()

                if (currentState != lastState){
                    withContext(Dispatchers.Main) {
                        onStateChange(currentState)
                    }
                    lastState = currentState
                }

                delay(2.seconds)
            }
        }
    }

    actual fun stopMonitoring(){
        monitoringJob?.cancel()
        monitoringJob = null
    }
}