package org.example.project.data.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.example.project.domain.models.PokemonTeamMember


class ShowdownClient(
    private val serverUrl: String = "ws://localhost:8000/showdown/websocket"
) {
    private val client = HttpClient(CIO) {
        install(WebSockets)
    }

    private var botSession: DefaultClientWebSocketSession? = null
    private var currentRoom: String? = null
    private var matchJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    suspend fun startMatch(
        playerUsername: String,
        botUsername: String,
        playerTeam: List<PokemonTeamMember>,  // kept for API compat; not used (player builds their own team)
        botTeam: List<PokemonTeamMember>,
        onMatchStarted: (roomId: String) -> Unit,
        onWin: (winner: String) -> Unit,
        onError: (String) -> Unit
    ) {
        matchJob?.cancel()
        matchJob = coroutineScope.launch {
            try {
                client.webSocket(serverUrl) {
                    botSession = this
                    println("[SHOWDOWN] Bot connected")
                    handleBotSession(
                        botUsername = botUsername,
                        playerUsername = playerUsername,
                        botTeam = botTeam,
                        onMatchStarted = onMatchStarted,
                        onWin = onWin
                    )
                }
            } catch (e: Exception) {
                println("[SHOWDOWN] Bot error: ${e.message}")
                onError(e.message ?: "Connection failed")
            }
        }
    }

    private suspend fun DefaultClientWebSocketSession.handleBotSession(
        botUsername: String,
        playerUsername: String,
        botTeam: List<PokemonTeamMember>,
        onMatchStarted: (String) -> Unit,
        onWin: (String) -> Unit,
    ) {
        var loggedIn = false
        var challengeSent = false
        var winReported = false

        for (frame in incoming) {
            if (frame !is Frame.Text) continue
            val message = frame.readText()
            println("[SHOWDOWN BOT] $message")

            // Showdown messages may have a room prefix line: ">battle-xyz\n|init|battle\n..."
            val lines = message.split("\n")
            val roomId = if (message.startsWith(">")) {
                lines.first().removePrefix(">").trim()
            } else ""

            for (line in lines) {
                when {
                    line.startsWith("|challstr|") -> {
                        // --no-security mode: log in by name only
                        send("|/trn $botUsername,0,")
                        println("[SHOWDOWN] Bot logging in as $botUsername")
                    }

                    line.startsWith("|updateuser|") &&
                            line.lowercase().contains("| ${botUsername.lowercase()}|1|") &&
                            !loggedIn -> {
                        loggedIn = true
                        println("[SHOWDOWN] Bot logged in as $botUsername")

                        delay(500)
                        send("|/utm ${generateShowdownExport(botTeam)}")
                        delay(500)

                        // Challenge the player. They must already be logged in on Showdown.
                        send("|/challenge $playerUsername, gen9customgame")
                        challengeSent = true
                        println("[SHOWDOWN] Bot challenged $playerUsername")
                    }

                    line.startsWith("|init|battle") && currentRoom == null -> {
                        currentRoom = roomId
                        onMatchStarted(roomId)
                        println("[SHOWDOWN] Battle room: $roomId")
                    }

                    line.startsWith("|request|") && roomId.isNotEmpty() -> {
                        val requestJson = line.substringAfter("|request|")
                        if (requestJson.isNotBlank()) {
                            delay(500)
                            chooseAction(roomId, requestJson)
                        }
                    }

                    line.startsWith("|win|") -> {
                        val winner = line.substringAfter("|win|").trim()
                        if (!winReported) {
                            winReported = true
                            println("[SHOWDOWN] Winner: $winner")
                            onWin(winner)
                        }
                    }

                    // Player rejected or didn't accept the challenge in time
                    line.startsWith("|popup|") && challengeSent -> {
                        val popup = line.substringAfter("|popup|")
                        println("[SHOWDOWN] Server popup: $popup")
                    }
                }
            }
        }
    }

    private suspend fun DefaultClientWebSocketSession.chooseAction(
        roomId: String,
        requestJson: String,
    ) {
        try {
            val request = Json.parseToJsonElement(requestJson).jsonObject

            // Team preview
            if (request["teamPreview"]?.jsonPrimitive?.booleanOrNull == true) {
                send("$roomId|/team 123456")
                println("[SHOWDOWN] Bot sent team order")
                return
            }

            // Force switch (current pokemon fainted)
            val forceSwitch = request["forceSwitch"]?.jsonArray
            if (forceSwitch != null && forceSwitch.firstOrNull()?.jsonPrimitive?.booleanOrNull == true) {
                val side = request["side"]?.jsonObject
                val pokemon = side?.get("pokemon")?.jsonArray
                val switchIndex = pokemon?.withIndex()?.firstOrNull { (_, p) ->
                    val obj = p.jsonObject
                    val active = obj["active"]?.jsonPrimitive?.booleanOrNull ?: false
                    val condition = obj["condition"]?.jsonPrimitive?.content ?: ""
                    !active && !condition.contains("fnt")
                }?.index

                if (switchIndex != null) {
                    send("$roomId|/choose switch ${switchIndex + 1}")
                    println("[SHOWDOWN] Bot switched to slot ${switchIndex + 1}")
                } else {
                    send("$roomId|/choose default")
                }
                return
            }

            // Wait turn (e.g., opponent is force-switching)
            if (request["wait"]?.jsonPrimitive?.booleanOrNull == true) {
                println("[SHOWDOWN] Bot waiting (no action required)")
                return
            }

            // Normal turn — pick first available move
            val active = request["active"]?.jsonArray?.firstOrNull()?.jsonObject
            val moves = active?.get("moves")?.jsonArray

            if (moves != null && moves.isNotEmpty()) {
                val moveIndex = moves.withIndex().firstOrNull { (_, m) ->
                    val obj = m.jsonObject
                    val disabled = obj["disabled"]?.jsonPrimitive?.booleanOrNull ?: false
                    val pp = obj["pp"]?.jsonPrimitive?.intOrNull ?: 1
                    !disabled && pp > 0
                }?.index

                if (moveIndex != null) {
                    send("$roomId|/choose move ${moveIndex + 1}")
                    println("[SHOWDOWN] Bot used move ${moveIndex + 1}")
                } else {
                    send("$roomId|/choose default")
                    println("[SHOWDOWN] Bot fell back to default (no usable moves)")
                }
            } else {
                send("$roomId|/choose default")
            }
        } catch (e: Exception) {
            println("[SHOWDOWN] Error parsing request: ${e.message}, falling back to default")
            send("$roomId|/choose default")
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
        matchJob?.cancel()
        matchJob = null
        botSession = null
        currentRoom = null
        try {
            client.close()
        } catch (e: Exception) {
            println("[SHOWDOWN] Error closing client: ${e.message}")
        }
    }
}