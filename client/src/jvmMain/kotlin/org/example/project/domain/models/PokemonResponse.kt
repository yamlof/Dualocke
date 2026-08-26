package org.example.project.domain.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
