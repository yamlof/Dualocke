package org.example.project.utils

fun getRankFromElo(elo: Int): String {
    return when {
        elo < 800 -> "Grunt"
        elo < 1000 -> "Novice"
        elo < 1200 -> "Rookie"
        elo < 1500 -> "Challenger"
        elo < 1800 -> "Gym Leader"
        elo < 2200 -> "Elite Four"
        else -> "Champion"
    }
}