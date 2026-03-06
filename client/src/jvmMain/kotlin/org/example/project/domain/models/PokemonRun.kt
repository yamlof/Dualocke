package org.example.project.domain.models

import kotlinx.serialization.Serializable

@Serializable
data class PokemonRun(
    val id:String,
    val trainerName:String,
    val gameName:String,
    val gameVersion:GameVersion,
    val saveFilePath:String,
    val badges: Int = 0,
    val deaths: Int = 0,
    val pokemonTeam : List<PokemonTeamMember> = emptyList(),
    val isActive: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val lastPlayedAt: Long = System.currentTimeMillis(),

    val emulatorSavePath:String? = null,
    val emulatorRomPath:String? = null
)

@Serializable
data class PokemonTeamMember(
    val name:String,
    val species:String,
    val level:Int,
    val iconUrl:String,
    val isAlive:Boolean = true
)

enum class GameVersion{
    FIRE_RED,
    LEAF_GREEN,
    EMERALD,
    RUBY,
    SAPPHIRE;

    fun getDisplayName():String = when(this){
        FIRE_RED -> "Pokémon FireRed"
        LEAF_GREEN -> "Pokémon LeafGreen"
        EMERALD -> "Pokémon Emerald"
        RUBY -> "Pokémon Ruby"
        SAPPHIRE -> "Pokémon Sapphire"
    }
}
