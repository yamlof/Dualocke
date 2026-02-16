package org.example.project.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import kotlinx.coroutines.launch
import java.io.File
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.Alignment
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import monitor.ProcessMonitor
import org.example.project.Profile
import org.example.project.SupabaseClient
import org.example.project.data.decodeFRString
import org.example.project.data.findSection
import org.example.project.data.getActiveFireRedSaveBase
import org.example.project.domain.models.PokemonResponse
import org.example.project.data.network.LuaTcpClient
import org.example.project.data.readSaveFile
import org.example.project.launchMGBA
import org.example.project.ui.home.components.LivePartyDataSection
import org.example.project.ui.home.components.MatchSection
import org.example.project.ui.home.components.QuickActionGrid
import org.example.project.ui.home.components.SelectedRunCard
import org.example.project.ui.home.components.SmallTopAppBar
import org.example.project.ui.home.components.WelcomeSection


@Composable
fun HomeScreen(
    onLogout: () -> Unit,
    isMgbaRunning : Boolean
) {
    val tcpClient = remember { LuaTcpClient() }
    var partyLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var connectionError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val saveData = readSaveFile("resources/roms/firered.sav")
    val saveBase = getActiveFireRedSaveBase(saveData)
    //println("Using FireRed save base: 0x${saveBase.toString(16)}")

    val trainerSection = findSection(saveData, saveBase, 0)
    val teamItemSection = findSection(saveData,saveBase,1)
    val teamSizeBytes = saveData.copyOfRange(teamItemSection + 0x0034,teamItemSection + 0x0034 + 1)
    val teamBytes = saveData.copyOfRange(teamItemSection + 0x0038 ,teamItemSection + 0x0038 + 600)
    val nameBytes = saveData.copyOfRange(trainerSection, trainerSection + 7)
    var isLoading by remember { mutableStateOf(true) }
    var profile by remember { mutableStateOf<Profile?>(null) }

    val viewModel = rememberPartyViewModel()


    LaunchedEffect(Unit){
        scope.launch {
            profile = SupabaseClient.getUsername()
            isLoading = false
        }
    }

    val pokeApiClient = HttpClient(CIO) {
        install(Logging){
            filter{ request ->
                request.url.host.contains("pokeapi.co")
            }
        }

        install(ContentNegotiation) {
            json(Json{
                ignoreUnknownKeys = true
            })
        }

    }

    val pokemonTeam = mutableListOf<ByteArray>()

    val teamSize = teamSizeBytes.joinToString(" ") { it.toString() }

    for (i in 0..<teamSize.toInt()){
        val start = i * 100
        val end = (i*100) + 100
        val pokemonData = teamBytes.copyOfRange(start,end)
        pokemonTeam.add(pokemonData)
    }

    val pokemonTeamIcon = mutableListOf<String>()

    runBlocking {
        for (pokemon in pokemonTeam) {
            val pokemonBytesName = pokemon.copyOfRange(0x08,0x08+10)
            println("Raw bytes: ${pokemonBytesName.joinToString(" ") { it.toUByte().toString() }}")
            val name = decodeFRString(pokemonBytesName)
            val formattedName = name.trim().lowercase()
            println("Extracted name: '$name'")  // <-- Add this
            println("Raw decoded: '$name' (length: ${name.length})")
            println("After trim: '${name.trim()}' (length: ${name.trim().length})")
            val bytesBeforeTerminator = pokemonBytesName.takeWhile { it.toInt() != 0xFF }
            println("Bytes kept by takeWhile: ${bytesBeforeTerminator.joinToString(" ") { it.toUByte().toString() }}")
            println("Count: ${bytesBeforeTerminator.size}")
            val testByte: Byte = 255.toByte()
            println("Byte value: $testByte")
            println("toInt(): ${testByte.toInt()}")
            println("toUByte().toInt(): ${testByte.toUByte().toInt()}")
            val pokemonTeamResponse = pokeApiClient.get("https://pokeapi.co/api/v2/pokemon/${formattedName}")
            //println(pokemonTeamResponse.bodyAsText())
            val pokemon : PokemonResponse = pokemonTeamResponse.body()
            val iconUrl = pokemon.sprites.versions.generation7.icons.front_default
            println(iconUrl)
            pokemonTeamIcon.add(iconUrl)
        }
    }

    println(teamSizeBytes.joinToString(" ") { it.toString() })
    //println(teamBytes.joinToString(" ") { it.toString() })
    //println(teamBytes.contentToString())

    //println(nameBytes.joinToString(" ") { "%02X".format(it) })
    //println("Player: ${decodeFRString(nameBytes)}")

    // Handle TCP connection lifecycle
    DisposableEffect(Unit) {
        val job = scope.launch {
            tcpClient.connect(
                onLine = { line ->
                    // Keep only last 100 lines to prevent memory issues
                    partyLines = (partyLines + line).takeLast(100)
                },
                onError = { error ->
                    connectionError = "Connection lost: ${error.message}"
                }
            )
        }

        onDispose {
            job.cancel()
            tcpClient.disconnect()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        SmallTopAppBar(onLogout = onLogout)

        Row (
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ){
            Column(
                modifier = Modifier
                    .weight(1.5f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
            ) {
                WelcomeSection(
                    username = profile?.username,
                    isLoading = isLoading
                )
                Spacer(modifier = Modifier.height(24.dp))

                SelectedRunCard(
                    trainerName = decodeFRString(nameBytes),
                    pokemonTeamIcon = pokemonTeamIcon
                )

                Spacer(modifier = Modifier.height(24.dp))

                MatchSection()

                Spacer(modifier = Modifier.height(24.dp))

                QuickActionGrid()

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { launchMGBA() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ){
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Launch Game",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                connectionError?.let { error ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row (
                            modifier = Modifier.padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ){
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                error,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
                LivePartyDataSection(
                    partyLines = partyLines,
                    isMgbaRunning = isMgbaRunning,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

class PartyViewModel : ViewModel() {
    private val processMonitor = ProcessMonitor()

    private val _isMgbaRunning = MutableStateFlow(false)
    val isMgbaRunning : StateFlow<Boolean> = _isMgbaRunning.asStateFlow()

    private val _partyLines = MutableStateFlow<List<String>>(emptyList())
    val partyLines : StateFlow<List<String>> = _partyLines.asStateFlow()

    init {
        processMonitor.startMonitoring { isRunning ->
            _isMgbaRunning.value = isRunning

            if (isRunning) {
                addPartyLine("Mgba emulator detected - Connected")
            } else {
                addPartyLine("Mgba emulator closed - Disconnected")
            }
        }
    }

    private fun addPartyLine(line: String){
        val timestamp = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val formattedLine = "[${timestamp.time}] $line"
        _partyLines.value = _partyLines.value + formattedLine
    }

    override fun onCleared() {
        super.onCleared()
        processMonitor.stopMonitoring()
    }
}

@Composable
fun rememberPartyViewModel() : PartyViewModel{
    return remember { PartyViewModel() }
}
