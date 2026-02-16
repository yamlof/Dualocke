package org.example.project.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Socket
import kotlin.coroutines.cancellation.CancellationException

class LuaTcpClient {
    private var socket: Socket? = null
    private var job: Job? = null

    suspend fun connect(
        host: String = "127.0.0.1",
        port: Int = 8888,
        onLine: (String) -> Unit,
        onError: (Exception) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        try {
            socket = Socket(host, port)
            val reader = BufferedReader(InputStreamReader(socket!!.getInputStream()))

            var line: String?
            while (isActive) {
                val line = reader.readLine() ?: break
                withContext(Dispatchers.Main) {
                    onLine(line)
                }
            }
        } catch (e: Exception) {
            if (e !is CancellationException) {
                println("TCP error: ${e.message}")
                withContext(Dispatchers.Main) {
                    onError(e)
                }
            }
        } finally {
            disconnect()
        }
    }

    fun disconnect() {
        try {
            socket?.close()
            socket = null
        } catch (_: Exception) {}
    }
}