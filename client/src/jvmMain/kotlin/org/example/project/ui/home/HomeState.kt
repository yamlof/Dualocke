package org.example.project.ui.home

import org.example.project.LeaderboardEntry
import org.example.project.Match
import  org.example.project.data.network.NuzlockeSnapshot
import org.example.project.domain.models.GameVersion

data class HomeUiState(
    val username:String? = null,
    val isLoading: Boolean = true,
    val rank:String = "",

    val currentRunId: String? = null,
    val trainerName : String = "",
    val gameName: String = "Pokemon FireRed",
    val badges: String = "0/8",
    val deaths:String = "0",
    val pokemonTeamIcons : List<String> = emptyList(),

    val partyLines: List<String> = emptyList(),
    val isConnected: Boolean = false,
    val connectionError:String? = null,

    val romLoaded: Boolean = false,
    val romName: String? = null,
    val gbaRunFolders: List<String> = emptyList(),
    val activeGbaRun: String? = null,
    val gbaError: String? = null,

    val isSwitchingRun: Boolean = false,

    val verifiedGames: Set<GameVersion> = emptySet(),
    val activeGame: GameVersion? = null,
    val slotsByGame: Map<GameVersion, List<String>> = emptyMap(),

    val latestSnapshot: NuzlockeSnapshot? = null,

    val showMatchDialog: Boolean = false,
    val currentMatch: Match? = null,
    val showdownRoomId: String? = null,
    val showdownUrl: String? = null,
    val showMatchResult: Boolean = false,
    val playerWon: Boolean? = null,
    val elo: Int = 1000,
    val leaderboard: List<LeaderboardEntry> = emptyList(),
    val showLeaderboard: Boolean = false
    )