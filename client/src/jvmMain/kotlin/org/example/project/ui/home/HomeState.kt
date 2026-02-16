package org.example.project.ui.home

data class HomeUiState(
    val username:String? = null,
    val isLoading: Boolean = true,
    val rank:String = "GrandMaster",

    // Run Data
    val trainerName : String = "",
    val gameName: String = "Pokemon FireRed",
    val badges: String = "0/8",
    val deaths:String = "0",
    val pokemonTeamIcons : List<String> = emptyList(),

    // TCP Data
    val partyLines: List<String> = emptyList(),
    val isConnected: Boolean = false,
    val connectionError:String? = null
)