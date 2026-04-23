package org.example.project.data.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.example.project.domain.models.PokemonTeamMember


class ShowdownClient(
    private val serverUrl: String = "ws://localhost:8000/showdown/websocket"
) {
    private val client = HttpClient(CIO) {
        install(WebSockets)
    }

    private var playerSession: DefaultClientWebSocketSession? = null
    private var botSession: DefaultClientWebSocketSession? = null
    private var currentRoom: String? = null
    @Volatile private var botReady = false
    private var storedPlayerTeam: List<PokemonTeamMember> = emptyList()
    private var storedBotTeam: List<PokemonTeamMember> = emptyList()

    suspend fun startMatch(
        playerUsername: String,
        botUsername: String,
        playerTeam: List<PokemonTeamMember>,
        botTeam: List<PokemonTeamMember>,
        onMatchStarted: (roomId: String) -> Unit,
        onWin: (winner: String) -> Unit,
        onError: (String) -> Unit
    ) {
        botReady = false
        storedPlayerTeam = playerTeam
        storedBotTeam = botTeam
        coroutineScope {
            // Connect player
            launch {
                try {
                    client.webSocket(serverUrl) {
                        playerSession = this
                        println("[SHOWDOWN] Player connected")
                        handlePlayerSession(
                            playerUsername,
                            botUsername,
                            onMatchStarted,
                            onWin
                        )
                    }
                } catch (e: Exception) {
                    println("[SHOWDOWN] Player error: ${e.message}")
                    onError(e.message ?: "Connection failed")
                }
            }

            // Connect bot
            launch {
                delay(2000) // wait for player to connect first
                try {
                    client.webSocket(serverUrl) {
                        botSession = this
                        println("[SHOWDOWN] Bot connected")
                        handleBotSession(botUsername, playerUsername)
                    }
                } catch (e: Exception) {
                    println("[SHOWDOWN] Bot error: ${e.message}")
                }
            }
        }
    }

    private suspend fun DefaultClientWebSocketSession.handlePlayerSession(
        playerUsername: String,
        botUsername: String,
        onMatchStarted: (String) -> Unit,
        onWin: (String) -> Unit
    ) {
        var loggedIn = false
        var inRoom = false

        for (frame in incoming) {
            if (frame !is Frame.Text) continue
            val message = frame.readText()
            println("[SHOWDOWN PLAYER] $message")

            val lines = message.split("\n")
            val roomId = if (message.startsWith(">")) {
                message.lines().first().removePrefix(">").trim()
            } else ""

            for (line in lines) {
                when {
                    line.startsWith("|challstr|") -> {
                        val challstr = line.substringAfter("|challstr|")
                        // Login without password (--no-security mode)
                        send("|/trn $playerUsername,0,")
                        println("[SHOWDOWN] Player logging in as $playerUsername")
                    }


                    line.startsWith("|updateuser|") &&
                            line.lowercase().contains("| ${playerUsername.lowercase()}|1|") && !loggedIn -> {
                        loggedIn = true
                        println("[SHOWDOWN] Player logged in as $playerUsername")
                        delay(500)
                        // Wait for bot to be ready
                        var waited = 0
                        while (!botReady && waited < 10000) {
                            delay(500)
                            waited += 500
                        }
                        println("[SHOWDOWN] Bot ready, sending challenge")
                        val export = generateShowdownExport(storedPlayerTeam)
                        println("[SHOWDOWN] Team export: $export")
                        send("|/utm $export")
                        delay(500)
                        send("|/challenge $botUsername, gen9customgame")
                        println("[SHOWDOWN] Challenge sent to $botUsername")
                    }




                    line.startsWith("|win|") -> {
                        val winner = line.substringAfter("|win|").trim()
                        println("[SHOWDOWN] Winner: $winner")
                        onWin(winner)
                    }

                    line.startsWith("|init|battle") && !inRoom -> {
                        inRoom = true
                        currentRoom = roomId
                        onMatchStarted(roomId)
                        println("[SHOWDOWN] Battle room: $roomId")
                    }

                    line.startsWith("|request|") && line.contains("teamPreview") && roomId.isNotEmpty() -> {
                        delay(500)
                        send("$roomId|/team 123")
                        println("[SHOWDOWN] Player sent team order")
                    }
                }
            }
        }
    }

    private suspend fun DefaultClientWebSocketSession.handleBotSession(
        botUsername: String,
        playerUsername: String,
    ) {
        var loggedIn = false

        for (frame in incoming) {
            if (frame !is Frame.Text) continue
            val message = frame.readText()
            println("[SHOWDOWN BOT] $message")

            val lines = message.split("\n")
            val roomId = if (message.startsWith(">")) {
                message.lines().first().removePrefix(">").trim()
            } else ""


            for (line in lines) {
                when {
                    line.startsWith("|challstr|") -> {
                        send("|/trn $botUsername,0,")
                        println("[SHOWDOWN] Bot logging in as $botUsername")
                    }

                    line.startsWith("|updateuser|") &&
                            line.lowercase().contains("| ${botUsername.lowercase()}|1|") && !loggedIn -> {
                        loggedIn = true
                        println("[SHOWDOWN] Bot logged in as $botUsername")
                        delay(500)
                        send("|/utm ${generateShowdownExport(storedBotTeam)}")
                        delay(500)
                        botReady = true
                        println("[SHOWDOWN] Bot ready")
                    }

                    // Accept incoming challenge from player
                    line.startsWith("|pm|") -> {
                        println("[SHOWDOWN BOT PM] $line")
                        if (line.contains("wants to battle") || line.contains("/challenge")) {
                            delay(500)
                            send("|/accept $playerUsername")
                            println("[SHOWDOWN] Bot accepted challenge")
                        }
                    }

                    // Bot forfeits on first request (teamPreview or active)
                    line.startsWith("|request|") && roomId.isNotEmpty() -> {
                        delay(500)
                        val request = line.substringAfter("|request|")
                        if (request.contains("teamPreview")) {
                            send("$roomId|/team 123")
                            delay(500)
                        }
                        send("$roomId|/forfeit")
                        println("[SHOWDOWN] Bot forfeited")
                    }
                }
            }
        }
    }

    fun generateShowdownExport(team: List<PokemonTeamMember>): String {
        return team.joinToString("]") { mon ->
            val speciesName = mon.species.replaceFirstChar { it.uppercase() }
            val nickname = mon.name.takeIf { it.uppercase() != speciesName.uppercase() } ?: speciesName
            "$nickname|$speciesName|||tackle,growl,leer,quickattack|||||||${mon.level}"
        }
    }

    fun disconnect() {
        client.close()
    }
}