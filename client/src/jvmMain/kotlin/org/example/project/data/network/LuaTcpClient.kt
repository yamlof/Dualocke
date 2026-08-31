package org.example.project.data.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket

data class PartyMon(
    val nickname: String,
    val species: String,
    val level: Int,
    val hp: Int,
    val maxHp: Int,
) {
    val isAlive: Boolean get() = hp > 0
    val hpPercent: Float get() = if (maxHp > 0) hp.toFloat() / maxHp else 0f
}

data class Death(
    val nickname: String,
    val species: String,
    val level: Int,
    val location: Int,
)

data class EncounterInfo(
    val species: String,
    val nickname: String?,
    val status: String,
) {
    val isCaught: Boolean get() = status == "caught"
    val isFailed: Boolean get() = status == "failed"
    val isInBattle: Boolean get() = status == "in_battle"
}

data class NuzlockeSnapshot(
    val mapId: Int,
    val badgeCount: Int,
    val party: List<PartyMon>,
    val deaths: List<Death>,
    val encounters: Map<Int, EncounterInfo>,
)

class LuaTcpClient {
    private val port = 8888
    @Volatile private var socket: Socket? = null

    fun startWithReconnect(
        scope: CoroutineScope,
        runId: String?,
        onLine: (String) -> Unit,
        onSnapshot: (NuzlockeSnapshot) -> Unit,
        onConnected: () -> Unit,
        onDisconnected: () -> Unit,
    ) {
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val s = Socket("localhost", port)
                    socket = s
                    onConnected()
                    s.use {
                        val reader = BufferedReader(InputStreamReader(it.inputStream))
                        val writer = PrintWriter(it.outputStream, true)
                        if (runId != null) writer.println("HELLO:$runId")
                        val snapshotLines = mutableListOf<String>()
                        var inSnapshot = false
                        while (isActive) {
                            val l = reader.readLine() ?: break
                            onLine(l)
                            when {
                                l == "SNAPSHOT_BEGIN" -> {
                                    inSnapshot = true
                                    snapshotLines.clear()
                                }
                                l == "SNAPSHOT_END" -> {
                                    inSnapshot = false
                                    onSnapshot(parseSnapshot(snapshotLines))
                                }
                                inSnapshot -> snapshotLines.add(l)
                            }
                        }
                    }
                } catch (_: Exception) {}
                socket = null
                onDisconnected()
                if (scope.isActive) delay(3000)
            }
        }
    }

    fun disconnect() {
        try { socket?.close() } catch (_: Exception) {}
        socket = null
    }

    private fun parseSnapshot(lines: List<String>): NuzlockeSnapshot {
        var mapId = 0
        var badgeCount = 0
        val party = mutableListOf<PartyMon>()
        val deaths = mutableListOf<Death>()
        val encounters = mutableMapOf<Int, EncounterInfo>()
        for (line in lines) {
            when {
                line.startsWith("MAP:") -> mapId = line.removePrefix("MAP:").toIntOrNull() ?: 0
                line.startsWith("BADGES:") -> badgeCount = line.removePrefix("BADGES:").toIntOrNull() ?: 0
                line.startsWith("MON:") -> {
                    // MON:<i>|<nickname>|<species>|<level>|<hp>|<maxHP>
                    val parts = line.removePrefix("MON:").split("|")
                    if (parts.size >= 6) {
                        party.add(PartyMon(
                            nickname = parts[1],
                            species = parts[2],
                            level = parts[3].toIntOrNull() ?: 0,
                            hp = parts[4].toIntOrNull() ?: 0,
                            maxHp = parts[5].toIntOrNull() ?: 1,
                        ))
                    }
                }
                line.startsWith("DEAD|") -> {
                    // DEAD|<nickname>|<species>|<level>|<location>|<frameCount>
                    val parts = line.split("|")
                    if (parts.size >= 5) {
                        deaths.add(Death(
                            nickname = parts[1],
                            species = parts[2],
                            level = parts[3].toIntOrNull() ?: 0,
                            location = parts[4].toIntOrNull() ?: 0,
                        ))
                    }
                }
                line.startsWith("ENCV2|") -> {
                    // ENCV2|<mapId>|<species>|<nickname>|<status>
                    val parts = line.split("|")
                    if (parts.size >= 5) {
                        val encMapId = parts[1].toIntOrNull() ?: continue
                        encounters[encMapId] = EncounterInfo(
                            species = parts[2],
                            nickname = parts[3].ifEmpty { null },
                            status = parts[4],
                        )
                    }
                }
            }
        }
        return NuzlockeSnapshot(mapId, badgeCount, party, deaths, encounters)
    }
}