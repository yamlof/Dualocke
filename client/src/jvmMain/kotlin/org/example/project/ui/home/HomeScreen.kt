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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import monitor.ProcessMonitor
import org.example.project.ui.home.components.GameLibraryDialog
import org.example.project.ui.home.components.LivePartyDataSection
import org.example.project.ui.home.components.MatchDialog
import org.example.project.ui.home.components.MatchSection
import org.example.project.ui.home.components.QuickActionGrid
import org.example.project.ui.home.components.SelectedRunCard
import org.example.project.ui.home.components.SmallTopAppBar
import org.example.project.ui.home.components.WelcomeSection
import org.example.project.utils.getRankFromElo


@Composable
fun HomeScreen(
    viewModel: HomeViewModel = remember { HomeViewModel() },
    onLogout: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val allRuns by viewModel.runRepository.allRuns.collectAsState()
    val scope = rememberCoroutineScope()

    var showGameLibrary by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        scope.launch {
            viewModel.startTcpConnection()
        }

        onDispose {
            viewModel.stopTcpConnection()
        }
    }

    HomeScreenContent(
        uiState = uiState,
        onLogout = onLogout,
        onLaunchGame = { viewModel.launchGame() },
        onFindCasualMatch = { viewModel.findCasualMatch() },
        onFindRankedMatch = { viewModel.findRankedMatch() },
        onChangeRun = { showGameLibrary = true },
        onViewLeaderboards = { viewModel.toggleLeaderboard() },
        onViewHistory = { viewModel.viewHistory() },
        onViewCommunity = { viewModel.toggleLeaderboard() },
        onOpenSettings = { viewModel.openSettings() }
    )

    if (showGameLibrary){
        GameLibraryDialog(
            onDismiss = { showGameLibrary = false },
            onGameSelected = { viewModel.switchGame(it) },
            onVerifyRom = { game, file -> viewModel.verifyRom(game, file) },
            onSwitchSlot = { game, slot -> viewModel.switchGbaRun(game, slot) },
            onCreateSlot = { game, name -> viewModel.createGbaRun(game, name) },
            onDeleteSlot = { game, name -> viewModel.deleteGbaRun(game, name) },
            verifiedGames = uiState.verifiedGames,
            activeGame = uiState.activeGame,
            activeSlot = uiState.activeGbaRun,
            slotsByGame = uiState.slotsByGame
        )
    }
    if (uiState.showMatchDialog && uiState.currentMatch != null) {
        MatchDialog(
            match = uiState.currentMatch!!,
            showdownUsername = uiState.showdownUsername,
            onUsernameChange = viewModel::updateShowdownUsername,
            showdownUrl = uiState.showdownUrl,
            onStartBattle = { viewModel.startShowdownMatch(uiState.currentMatch!!) },
            onWin = viewModel::reportWin,
            onLoss = viewModel::reportLoss,
            onDismiss = viewModel::dismissMatchResult
        )
    }

    if (uiState.showMatchResult) {
        AlertDialog(
            onDismissRequest = { },
            title = {
                Text(if (uiState.playerWon == true) "🏆 Victory!" else "💀 Defeated!")
            },
            text = {
                Text(
                    if (uiState.playerWon == true)
                        "Congratulations! Your Elo has been updated."
                    else
                        "Better luck next time! Your Elo has been updated."
                )
            },
            confirmButton = {
                Button(onClick = { viewModel.dismissMatchResult() }) {
                    Text("OK")
                }
            }
        )
    }


    if (uiState.showLeaderboard) {
        Dialog(onDismissRequest = { viewModel.toggleLeaderboard() }) {
            Card(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        "🏆 Leaderboard",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    uiState.leaderboard.forEachIndexed { index, entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "#${index + 1}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = when(index) {
                                    0 -> Color(0xFFFFD700)
                                    1 -> Color(0xFFC0C0C0)
                                    2 -> Color(0xFFCD7F32)
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier.width(40.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    entry.username,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    getRankFromElo(entry.elo),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "${entry.elo} ELO",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "${entry.wins}W ${entry.losses}L",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (index < uiState.leaderboard.size - 1) {
                            HorizontalDivider()
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.toggleLeaderboard() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Close")
                    }
                }
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

@Composable
private fun HomeScreenContent(
    uiState: HomeUiState,
    onLogout: () -> Unit,
    onLaunchGame :() -> Unit,
    onFindCasualMatch:() -> Unit,
    onFindRankedMatch: () -> Unit,
    onChangeRun: () -> Unit,
    onViewLeaderboards: () -> Unit,
    onViewHistory: () -> Unit,
    onViewCommunity: () -> Unit,
    onOpenSettings: () -> Unit,
){
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        SmallTopAppBar(onLogout = onLogout)

        // Desktop layout with two columns
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Left Column Main Content
            Column(
                modifier = Modifier
                    .weight(1.5f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
            ) {
                WelcomeSection(
                    username = uiState.username,
                    isLoading = uiState.isLoading,
                    rank = uiState.rank,
                    elo = uiState.elo
                )

                Spacer(modifier = Modifier.height(24.dp))

                SelectedRunCard(
                    trainerName = uiState.trainerName,
                    gameName = uiState.gameName,
                    badges = uiState.badges,
                    deaths = uiState.deaths,
                    pokemonTeamIcon = uiState.pokemonTeamIcons,
                    activeGame = uiState.activeGame,
                    activeSlot = uiState.activeGbaRun,
                    onChangeRun = onChangeRun
                )


                Spacer(modifier = Modifier.height(24.dp))

                MatchSection(
                    onCasualClick = onFindCasualMatch,
                    onRankedClick = onFindRankedMatch
                )

                Spacer(modifier = Modifier.height(24.dp))

                QuickActionGrid(
                    onLeaderBoardsClick = onViewLeaderboards,
                    onHistoryClick = onViewHistory,
                    onCommunityClick = onViewCommunity,
                    onSettingsClick = onOpenSettings
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onLaunchGame,
                    enabled = uiState.romLoaded && !uiState.isSwitchingRun && uiState.activeGbaRun != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    if (uiState.isSwitchingRun) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Switching Run...",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            if (uiState.romLoaded) "Launch Game" else "No ROM Loaded",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            // Right Column Live Data & Info
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                uiState.connectionError?.let { error ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
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
                    partyLines = uiState.partyLines,
                    isMgbaRunning = uiState.isConnected,
                    snapshot = uiState.latestSnapshot,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}