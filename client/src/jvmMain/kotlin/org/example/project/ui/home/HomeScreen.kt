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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import monitor.ProcessMonitor
import org.example.project.ui.home.components.LivePartyDataSection
import org.example.project.ui.home.components.MatchSection
import org.example.project.ui.home.components.QuickActionGrid
import org.example.project.ui.home.components.RunSelectionDialog
import org.example.project.ui.home.components.SelectedRunCard
import org.example.project.ui.home.components.SmallTopAppBar
import org.example.project.ui.home.components.WelcomeSection


@Composable
fun HomeScreen(
    viewModel: HomeViewModel = remember { HomeViewModel() },
    onLogout: () -> Unit,

) {
    val uiState by viewModel.uiState.collectAsState()
    val allRuns by viewModel.runRepository.allRuns.collectAsState()
    val scope = rememberCoroutineScope()

    var showRunSelection by remember { mutableStateOf(false) }



    // Handle TCP connection lifecycle
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
        onChangeRun = { showRunSelection = true },
        onViewLeaderboards = { viewModel.viewLeaderboards() },
        onViewHistory = { viewModel.viewHistory() },
        onViewCommunity = { viewModel.viewCommunity() },
        onOpenSettings = { viewModel.openSettings() }
    )

    if (showRunSelection){
        RunSelectionDialog(
            allRuns = allRuns,
            currentRunId = uiState.currentRunId,
            onRunSelected = {runId ->
                viewModel.switchRun(runId)
            },
            onImportRom = {file ->
                viewModel.importRom(file)
            },
            onDeleteRun = {runId ->
                viewModel.deleteRun(runId)
            },
            onDismiss = {showRunSelection=false }
        )
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
    onOpenSettings: () -> Unit
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
            // Left Column - Main Content
            Column(
                modifier = Modifier
                    .weight(1.5f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
            ) {
                WelcomeSection(
                    username = uiState.username,
                    isLoading = uiState.isLoading,
                    rank = uiState.rank
                )

                Spacer(modifier = Modifier.height(24.dp))

                SelectedRunCard(
                    trainerName = uiState.trainerName,
                    gameName = uiState.gameName,
                    badges = uiState.badges,
                    deaths = uiState.deaths,
                    pokemonTeamIcon = uiState.pokemonTeamIcons,
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
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

            // Right Column - Live Data & Info
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
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}