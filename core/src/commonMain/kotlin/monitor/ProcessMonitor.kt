package monitor

expect class ProcessMonitor(){
    fun isMgbaRunning(): Boolean
    fun startMonitoring(onStateChange:(Boolean) -> Unit)
    fun stopMonitoring()
}