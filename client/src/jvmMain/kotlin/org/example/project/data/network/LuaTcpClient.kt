package org.example.project.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket
import java.net.SocketException
import kotlin.coroutines.cancellation.CancellationException

class LuaTcpClient (
    private val host: String = "localhost",
    private val port: Int = 8888
){
    private var socket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: PrintWriter? = null
    private var isConnected = false

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _latestSnapshot = MutableStateFlow<NuzlockeSnapshot?>(null)
    val latestSnapshot: StateFlow<NuzlockeSnapshot?> = _latestSnapshot.asStateFlow()

    /**
     * Connect to the mGBA Lua script
     * @param runId Optional run identifier to send to Lua
     * @param onLine Callback for each line received (for raw display)
     * @param onSnapshot Callback when a complete snapshot is parsed
     * @param onError Callback for connection errors
     */
    suspend fun connect(
        runId: String? = null,
        onLine: ((String) -> Unit)? = null,
        onSnapshot: ((NuzlockeSnapshot) -> Unit)? = null,
        onError: ((Exception) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        try {
            println("[TCP] Connecting to $host:$port...")
            _connectionState.value = ConnectionState.CONNECTING

            socket = Socket(host, port)
            reader = BufferedReader(InputStreamReader(socket!!.getInputStream()))
            writer = PrintWriter(socket!!.getOutputStream(), true)
            isConnected = true

            _connectionState.value = ConnectionState.CONNECTED
            println("[TCP] Connected successfully")

            // Read greeting
            val greeting = reader?.readLine()
            println("[TCP] Server: $greeting")

            // Send HELLO with run ID if provided
            if (runId != null) {
                sendMessage("HELLO:$runId")
            }

            // Main read loop
            var currentSnapshot: MutableList<String>? = null

            while (isConnected && isActive) {
                val line = reader?.readLine() ?: break

                // Call raw line callback
                onLine?.invoke(line)

                // Parse snapshot protocol
                when {
                    line == "SNAPSHOT_BEGIN" -> {
                        currentSnapshot = mutableListOf()
                    }
                    line == "SNAPSHOT_END" -> {
                        currentSnapshot?.let { lines ->
                            try {
                                val snapshot = parseSnapshot(lines)
                                _latestSnapshot.value = snapshot
                                onSnapshot?.invoke(snapshot)

                                // Send ACK back to Lua
                                sendMessage("ACK:${snapshot.sequence}")
                            } catch (e: Exception) {
                                println("[TCP] Error parsing snapshot: ${e.message}")
                            }
                        }
                        currentSnapshot = null
                    }
                    line.startsWith("NUZLOCKE_TRACKER") -> {
                        println("[TCP] Protocol: $line")
                    }
                    line.startsWith("HELLO_ACK:") -> {
                        val ackRunId = line.substringAfter("HELLO_ACK:")
                        println("[TCP] Run ID acknowledged: $ackRunId")
                    }
                    line == "PONG" -> {
                        println("[TCP] Ping response received")
                    }
                    currentSnapshot != null -> {
                        currentSnapshot.add(line)
                    }
                    else -> {
                        // Other messages (key presses, etc.)
                        // Already handled by onLine callback
                    }
                }
            }

        } catch (e: SocketException) {
            println("[TCP] Socket error: ${e.message}")
            _connectionState.value = ConnectionState.ERROR
            onError?.invoke(e)
        } catch (e: Exception) {
            println("[TCP] Connection error: ${e.message}")
            _connectionState.value = ConnectionState.ERROR
            onError?.invoke(e)
        } finally {
            disconnect()
        }
    }

    /**
     * Parse a snapshot from the Lua script
     */
    private fun parseSnapshot(lines: List<String>): NuzlockeSnapshot {
        var sequence = 0
        var frameCount = 0
        var mapId = 0
        var badges = 0
        var checksum: Long? = null
        val party = mutableListOf<PartyMon>()
        val deaths = mutableListOf<Death>()
        val encounters = mutableMapOf<Int, String>()

        for (line in lines) {
            when {
                line.startsWith("SEQ:") -> {
                    sequence = line.substringAfter("SEQ:").toIntOrNull() ?: 0
                }
                line.startsWith("FRAME:") -> {
                    frameCount = line.substringAfter("FRAME:").toIntOrNull() ?: 0
                }
                line.startsWith("MAP:") -> {
                    mapId = line.substringAfter("MAP:").toIntOrNull() ?: 0
                }
                line.startsWith("BADGES:") -> {
                    badges = line.substringAfter("BADGES:").toIntOrNull() ?: 0
                }
                line.startsWith("MON:") -> {
                    // Format: MON:1|CHARIZARD|Charizard|36|89|120
                    val parts = line.substringAfter("MON:").split("|")
                    if (parts.size >= 6) {
                        party.add(PartyMon(
                            position = parts[0].toIntOrNull() ?: 0,
                            nickname = parts[1],
                            species = parts[2],
                            level = parts[3].toIntOrNull() ?: 0,
                            hp = parts[4].toIntOrNull() ?: 0,
                            maxHp = parts[5].toIntOrNull() ?: 0
                        ))
                    }
                }
                line.startsWith("DEAD|") -> {
                    // Format: DEAD|PIDGEOT|Pidgeot|42|15|123456
                    val parts = line.substringAfter("DEAD|").split("|")
                    if (parts.size >= 5) {
                        deaths.add(Death(
                            nickname = parts[0],
                            species = parts[1],
                            level = parts[2].toIntOrNull() ?: 0,
                            location = parts[3].toIntOrNull() ?: 0,
                            frameCount = parts[4].toLongOrNull() ?: 0
                        ))
                    }
                }
                line.startsWith("ENC|") -> {
                    // Format: ENC|15|Pidgey
                    val parts = line.substringAfter("ENC|").split("|")
                    if (parts.size >= 2) {
                        val encMapId = parts[0].toIntOrNull() ?: 0
                        encounters[encMapId] = parts[1]
                    }
                }
                line.startsWith("CHECKSUM:") -> {
                    checksum = line.substringAfter("CHECKSUM:").toLongOrNull()
                }
            }
        }

        return NuzlockeSnapshot(
            sequence = sequence,
            frameCount = frameCount,
            mapId = mapId,
            badges = badges,
            badgeCount = badges.countOneBits(),
            party = party,
            deaths = deaths,
            encounters = encounters,
            checksum = checksum
        )
    }

    /**
     * Send a message to the Lua script
     */
    fun sendMessage(message: String) {
        try {
            writer?.println(message)
            writer?.flush()
            println("[TCP] Sent: $message")
        } catch (e: Exception) {
            println("[TCP] Error sending message: ${e.message}")
        }
    }

    /**
     * Send a ping to check connection
     */
    fun ping() {
        sendMessage("PING")
    }

    /**
     * Disconnect from the server
     */
    fun disconnect() {
        isConnected = false
        try {
            reader?.close()
            writer?.close()
            socket?.close()
            println("[TCP] Disconnected")
        } catch (e: Exception) {
            println("[TCP] Error during disconnect: ${e.message}")
        } finally {
            socket = null
            reader = null
            writer = null
            _connectionState.value = ConnectionState.DISCONNECTED
        }
    }

}


enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

/**
 * Complete snapshot from the Nuzlocke tracker
 */
data class NuzlockeSnapshot(
    val sequence: Int,
    val frameCount: Int,
    val mapId: Int,
    val badges: Int,
    val badgeCount: Int,
    val party: List<PartyMon>,
    val deaths: List<Death>,
    val encounters: Map<Int, String>,
    val checksum: Long?
)

/**
 * Party Pokemon
 */
data class PartyMon(
    val position: Int,
    val nickname: String,
    val species: String,
    val level: Int,
    val hp: Int,
    val maxHp: Int
) {
    val isAlive: Boolean get() = hp > 0
    val hpPercent: Float get() = if (maxHp > 0) (hp.toFloat() / maxHp) else 0f
}

/**
 * Death record
 */
data class Death(
    val nickname: String,
    val species: String,
    val level: Int,
    val location: Int,
    val frameCount: Long
)
