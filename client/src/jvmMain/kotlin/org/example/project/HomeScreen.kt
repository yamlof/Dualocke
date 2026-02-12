package org.example.project

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import coil3.compose.AsyncImage
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.Socket
import kotlin.coroutines.cancellation.CancellationException
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.get
import io.ktor.client.request.request
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType.Application.Json
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.http.HttpResponse
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.*
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ModifierLocalBeyondBoundsLayout
import androidx.compose.ui.text.font.FontFamily
import io.github.jan.supabase.realtime.Column
import io.ktor.http.contentRangeHeaderValue
import kotlinproject.client.generated.resources.Res
import org.jetbrains.skia.Surface
import java.nio.file.WatchEvent
import javax.management.Descriptor


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


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmallTopAppBarExample(
    onLogout: () -> Unit
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.primary,
        ),
        title = {
            Text(text = "Nuzlocke League", style = MaterialTheme.typography.headlineMedium)
        },
        actions = {

            TextButton(onClick = {}) {
                Text("Profile", color = MaterialTheme.colorScheme.primary)
            }

            TextButton(onClick = onLogout){
                Text("Logout", color = MaterialTheme.colorScheme.primary)
            }
        }
    )
}

fun getActiveFireRedSaveBase(data: ByteArray): Int {
    fun maxSaveIndex(base: Int): Long {
        var max = -1L
        repeat(14) { i ->
            val section = base + i * 0x1000
            val id = data.readUInt16LE(section + 0x0FF4)
            if (id in 0..13) {
                val saveIndex = data.readUInt32LE(section + 0x0FFC)
                max = maxOf(max, saveIndex)
            }
        }
        return max
    }

    val baseA = 0x00000
    val baseB = 0x0E000

    val a = maxSaveIndex(baseA)
    val b = maxSaveIndex(baseB)

    println("Save A max index: $a")
    println("Save B max index: $b")

    return if (b >= a) baseB else baseA
}
fun readSaveFile(path: String): ByteArray {
    return File(path).readBytes()
}
fun ByteArray.readUInt16LE(offset: Int): Int {
    return (this[offset].toInt() and 0xFF) or
            ((this[offset + 1].toInt() and 0xFF) shl 8)
}

fun ByteArray.readUInt32LE(offset: Int): Long {
    return (this[offset].toLong() and 0xFF) or
            ((this[offset + 1].toLong() and 0xFF) shl 8) or
            ((this[offset + 2].toLong() and 0xFF) shl 16) or
            ((this[offset + 3].toLong() and 0xFF) shl 24)
}
fun getSaveIndex(data: ByteArray): Int {
    val a = data.readUInt32LE(0x0FFC)
    val b = data.readUInt32LE(0x10FFC)

    return when {
        a == 0xFFFFFFFFL && b != 0xFFFFFFFFL -> 0x10000
        b == 0xFFFFFFFFL && a != 0xFFFFFFFFL -> 0x00000
        b > a -> 0x10000
        else -> 0x00000
    }
}
fun readString(data: ByteArray, offset: Int, length: Int): String {
    val chars = data.copyOfRange(offset, offset + length)
    return chars
        .takeWhile { it.toInt() != 0xFF }
        .map { it.toInt().toChar() }
        .joinToString("")
}

fun findSection(data: ByteArray, base: Int, targetId: Int): Int {
    repeat(14) { i ->
        val section = base + i * 0x1000
        val id = data.readUInt16LE(section + 0xFF4)
        if (id == targetId) return section
    }
    error("Section $targetId not found")
}

val frCharset = mapOf(
    // Space and punctuation
    0x00 to " ",
    0x01 to "À",
    0x02 to "Á",
    0x03 to "Â",
    0x04 to "Ç",
    0x05 to "È",
    0x06 to "É",
    0x07 to "Ê",
    0x08 to "Ë",
    0x09 to "Ì",
    0x0B to "Î",
    0x0C to "Ï",
    0x0D to "Ò",
    0x0E to "Ó",
    0x0F to "Ô",
    0x10 to "Œ",
    0x11 to "Ù",
    0x12 to "Ú",
    0x13 to "Û",
    0x14 to "Ñ",
    0x15 to "ß",
    0x16 to "à",
    0x17 to "á",
    0x19 to "ç",
    0x1A to "è",
    0x1B to "é",
    0x1C to "ê",
    0x1D to "ë",
    0x1E to "ì",
    0x20 to "î",
    0x21 to "ï",
    0x22 to "ò",
    0x23 to "ó",
    0x24 to "ô",
    0x25 to "œ",
    0x26 to "ù",
    0x27 to "ú",
    0x28 to "û",
    0x29 to "ñ",
    0x2A to "º",
    0x2B to "ª",
    0x2C to "ᵉʳ",
    0x2D to "&",
    0x2E to "+",

    0x34 to "Lv",
    0x35 to "=",
    0x36 to ";",

    // Special characters
    0x51 to "¿",
    0x52 to "¡",
    0x53 to "PK",
    0x54 to "MN",
    0x55 to "PO",
    0x56 to "Ké",
    0x57 to "BL",
    0x58 to "OC",
    0x59 to "K",

    0x68 to "Í",
    0x69 to "%",
    0x6A to "(",
    0x6B to ")",

    0x79 to "â",

    0x7A to "í",

    0x84 to "ᵉ",
    0x85 to "<",
    0x86 to ">",

    // Uppercase letters A-Z
    0xBB to "A",
    0xBC to "B",
    0xBD to "C",
    0xBE to "D",
    0xBF to "E",
    0xC0 to "F",
    0xC1 to "G",
    0xC2 to "H",
    0xC3 to "I",
    0xC4 to "J",
    0xC5 to "K",
    0xC6 to "L",
    0xC7 to "M",
    0xC8 to "N",
    0xC9 to "O",
    0xCA to "P",
    0xCB to "Q",
    0xCC to "R",
    0xCD to "S",
    0xCE to "T",
    0xCF to "U",
    0xD0 to "V",
    0xD1 to "W",
    0xD2 to "X",
    0xD3 to "Y",
    0xD4 to "Z",

    // Lowercase letters a-z
    0xD5 to "a",
    0xD6 to "b",
    0xD7 to "c",
    0xD8 to "d",
    0xD9 to "e",
    0xDA to "f",
    0xDB to "g",
    0xDC to "h",
    0xDD to "i",
    0xDE to "j",
    0xDF to "k",
    0xE0 to "l",
    0xE1 to "m",
    0xE2 to "n",
    0xE3 to "o",
    0xE4 to "p",
    0xE5 to "q",
    0xE6 to "r",
    0xE7 to "s",
    0xE8 to "t",
    0xE9 to "u",
    0xEA to "v",
    0xEB to "w",
    0xEC to "x",
    0xED to "y",
    0xEE to "z",

    // Numbers 0-9
    0xA1 to "0",
    0xA2 to "1",
    0xA3 to "2",
    0xA4 to "3",
    0xA5 to "4",
    0xA6 to "5",
    0xA7 to "6",
    0xA8 to "7",
    0xA9 to "8",
    0xAA to "9",

    // Punctuation
    0xAB to "!",
    0xAC to "?",
    0xAD to ".",
    0xAE to "-",
    0xAF to "·",
    0xB0 to "…",
    0xB1 to """,
        0xB2 to """,
    0xB3 to "'",
    0xB4 to "'",
    0xB5 to "♂",
    0xB6 to "♀",
    0xB7 to "$",
    0xB8 to ",",
    0xB9 to "×",
    0xBA to "/",

    // Control characters
    0xFC to "\n",  // Newline
    0xFD to "\n",  // Prompt for next page
    0xFE to "\n",  // Buffer
    0xFF to ""     // String terminator
)

fun decodeFRString(bytes: ByteArray): String =
    bytes
        .takeWhile { it.toUByte().toInt() != 0xFF }
        .map { frCharset[it.toInt() and 0xFF] ?: '?' }
        .joinToString("")

@Serializable
data class PokemonResponse(
    val sprites : Sprites
)

@Serializable
data class Sprites(
    val versions : Versions
)

@Serializable
data class Versions(
    @SerialName("generation-vii") val generation7 : GenerationVii
)

@Serializable
data class GenerationVii(
    val icons : IconsPokemon
)

@Serializable
data class IconsPokemon(
    val front_default : String
)



@Composable
fun HomeScreen(
    onLogout: () -> Unit
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
        SmallTopAppBarExample(onLogout = onLogout)

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
                    modifier = Modifier.weight(1f)
                )
            }
        }
        /*

        Row(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {

            Column{
                Text("Selected Run")
                Text(decodeFRString(nameBytes))
                Text("Game:")
                Text("Badges:")
                Text("Team")
                Row{
                    LazyRow(){
                        items(pokemonTeamIcon) {images ->
                            AsyncImage(
                                model = images,
                                ""
                            )
                        }
                    }
                }
                Text("Total Deaths:")
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ){
            Text("View LeaderBoards")
            Text("Match History")
            Text("Community")
        }

        Button(onClick = { launchMGBA() }) {
            Text("Start")
        }

        connectionError?.let { error ->
            Text(
                error,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(8.dp)
            )
        }

        Text("Live Party Data")
        Spacer(modifier = Modifier.height(8.dp))



        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            items(partyLines) { line ->
                Text(line, modifier = Modifier.padding(4.dp))
            }
        }*/
    }
}

@Composable
fun WelcomeSection(
    username:String?,
    isLoading: Boolean
){
    Column {
        Text(
            text = if (isLoading) "Loading..." else "Welcome back,",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = username ?: "Please set a username",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.padding(vertical = 4.dp)
        ){
            Row {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "Grandmaster",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun MatchSection(){
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "Find Match",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline)
                ){
                    Text(
                        "Casual Mode",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(14.dp)
                ){
                    Row (
                        verticalAlignment = Alignment.CenterVertically
                    ){
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            modifier = Modifier
                                .size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Ranked Mode",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SelectedRunCard(
    trainerName: String,
    pokemonTeamIcon: List<String>
){
    Card (
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ){
        Column(
            modifier = Modifier.padding(28.dp)
        ) {
            Row (
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ){
                Text(
                    "Selected Run",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = {},
                    modifier = Modifier.size(40.dp)
                ){
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Change Run",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier
                    .fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(72.dp)
                ){
                    Box(
                        contentAlignment = Alignment.Center
                    ){
                        Text(
                            trainerName.firstOrNull()?.uppercase() ?: "?",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.width(20.dp))
                Column{
                    Text(
                        trainerName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row (
                        verticalAlignment = Alignment.CenterVertically
                    ){
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Pokemon FireRed",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(28.dp))

            Row (
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ){
                StatItem(
                    label = "Badges",
                    value = "0/8"
                )
                VerticalDivider(
                    modifier = Modifier
                        .height(60.dp)
                        .padding(vertical = 8.dp)
                )
                StatItem(
                    label = "Deaths",
                    value = "0"
                )
                VerticalDivider(
                    modifier = Modifier
                        .height(60.dp)
                        .padding(vertical = 8.dp)
                )
                StatItem(
                    label = "Team Size",
                    value = pokemonTeamIcon.size.toString()
                )
            }
            if (pokemonTeamIcon.isNotEmpty()) {
                Spacer(modifier = Modifier.height(28.dp))

                Row (
                    modifier = Modifier
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ){
                    Icon(
                        imageVector = Icons.Default.Face,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Current Team",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                LazyRow (
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ){
                    items(pokemonTeamIcon) { imageUrl ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(80.dp),
                            shadowElevation = 2.dp
                        ){
                            AsyncImage(
                                model = imageUrl,
                                contentDescription = "Pokemon",
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatItem (
    label:String,
    value: String
){
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ){
        Text(
            value,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun QuickActionGrid(){
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ){
        Row (
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ){
            QuickActionCard(
                title = "LeaderBoards",
                icon = Icons.Default.List,
                description = "View Rankings",
                modifier = Modifier.weight(1f),
                onClick = {}
            )
            QuickActionCard(
                title = "Match History",
                icon = Icons.Default.Done,
                description = "Past battles",
                modifier = Modifier.weight(1f),
                onClick = {}
            )
            Row (
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ){
                QuickActionCard(
                    title = "Community",
                    icon = Icons.Default.Person,
                    description = "Connect",
                    modifier = Modifier.weight(1f),
                    onClick = {}
                )
                QuickActionCard(
                    title = "Settings",
                    icon = Icons.Default.Settings,
                    description = "Preferences",
                    modifier = Modifier.weight(1f),
                    onClick = {}
                )
            }


        }
    }
}

@Composable
fun QuickActionCard(
    title:String,
    icon: ImageVector,
    description:String,
    modifier: Modifier = Modifier,
    onClick : () -> Unit
){
    Card(
        modifier = modifier
            .height(100.dp),
        shape = RoundedCornerShape(16.dp),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column (
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.Center
        ){
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )

        }
    }
}

@Composable
fun LivePartyDataSection(
    partyLines:List<String>,
    modifier: Modifier = Modifier
){
    Column(
        modifier = modifier
    ) {
        Row (
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ){
            Row (
                verticalAlignment = Alignment.CenterVertically
            ){
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Live Party Data",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF4CAF50).copy(alpha = 0.15f)
            ) {
                Row (
                    modifier = Modifier.padding(
                        horizontal = 14.dp,
                        vertical = 8.dp
                    ),
                    verticalAlignment = Alignment.CenterVertically
                ){
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(Color(0xFF4CAF50),CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Connected",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFF2E7D32),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            if (partyLines.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ){
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Waiting for party data...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(partyLines) { line ->
                        Text(line,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}