package org.example.project.data.network

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import org.example.project.LeaderboardEntry
import org.example.project.Match
import org.example.project.MatchInsert
import org.example.project.MatchQueueEntry
import org.example.project.MatchQueueInsert
import org.example.project.PlayerRating
import org.example.project.PlayerRatingInsert
import org.example.project.Profile
import org.example.project.data.RunRepository
import org.example.project.data.UserSession
import org.example.project.domain.models.PokemonTeamMember

object SupabaseClient {
    val client = createSupabaseClient(
        supabaseKey = "sb_publishable_P6DJHLxxuvWKgmFcK5rS1w_PwQUsc49",
        supabaseUrl = "https://jeiuhnrcakstcwenurci.supabase.co"
    ){
        install(Auth)
        install(Postgrest)

        defaultSerializer = KotlinXSerializer(Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
        })
    }

    suspend fun initializeSession() {
        client.auth.awaitInitialization()

        val userId = client.auth.currentUserOrNull()?.id
        if (userId != null) {
            UserSession.setUser(userId)
            println("Restored session for user: $userId")
        } else {
            println("ℹ️ No restored session — running as guest")
        }
        RunRepository.initialize()

    }


    suspend fun register(email: String, username: String, password: String) {
        val result = client.auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }

        val userId = client.auth.currentUserOrNull()?.id ?: error("user not available")

        client.from("profiles")
            .insert(
                Profile(
                    id = userId,
                    email = email,
                    username = username
                )
            )

        UserSession.setUser(userId)
        RunRepository.reloadForUser()
    }


    suspend fun login(email: String, password: String) {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }

        val userId = client.auth.currentUserOrNull()?.id
            ?: error("Login succeeded but no user ID")
        UserSession.setUser(userId)
        RunRepository.reloadForUser()
    }


    suspend fun logout() {
        client.auth.signOut()

        UserSession.clear()
        RunRepository.reloadForUser()
    }

    suspend fun getUsername(): Profile? {
        val user = client.auth.currentUserOrNull() ?: return null

        return client
            .from("profiles")
            .select {
                filter {
                    eq("id",user.id)
                }
            }
            .decodeSingleOrNull<Profile>()

    }

    suspend fun queueForMatch(badgeCount: Int, team: List<PokemonTeamMember>, deaths: Int): String? {
        val userId = client.auth.currentUserOrNull()?.id ?: return null
        val rating = getPlayerRating(userId)
        val teamJson = Json.encodeToJsonElement(team) as JsonArray

        client.from("match_queue")
            .delete { filter { eq("player_id", userId) } }

        client.from("match_queue")
            .insert(MatchQueueInsert(
                player_id = userId,
                badge_count = badgeCount,
                team = teamJson,
                deaths = deaths,
                elo = rating?.elo ?: 1000
            ))

        println("Queued for match: badge $badgeCount")
        return userId
    }

    suspend fun findMatch(badgeCount: Int): MatchQueueEntry? {
        val userId = client.auth.currentUserOrNull()?.id ?: return null

        // Find opponent at same badge count (prefer bots for demo)
        val opponents = client.from("match_queue")
            .select {
                filter {
                    eq("badge_count", badgeCount)
                    neq("player_id", userId)
                }
            }
            .decodeList<MatchQueueEntry>()

        return opponents.firstOrNull()
    }

    suspend fun createMatch(
        opponent: MatchQueueEntry,
        myTeam: List<PokemonTeamMember>,
        myDeaths: Int,
        badgeCount: Int
    ): Match? {
        val userId = client.auth.currentUserOrNull()?.id ?: return null
        val myRating = getPlayerRating(userId)
        val myTeamJson = Json.encodeToJsonElement(myTeam) as JsonArray

        val match = Match(
            player1_id = userId,
            player2_id = opponent.player_id,
            player1_team = myTeamJson,
            player2_team = opponent.team,
            player1_deaths = myDeaths,
            player2_deaths = opponent.deaths,
            badge_count = badgeCount,
            player1_elo_before = myRating?.elo ?: 1000,
            player2_elo_before = opponent.elo
        )

        val created = client.from("matches")
            .insert(MatchInsert(
                player1_id = userId,
                player2_id = opponent.player_id,
                player1_team = myTeamJson,
                player2_team = opponent.team,
                player1_deaths = myDeaths,
                player2_deaths = opponent.deaths,
                badge_count = badgeCount,
                player1_elo_before = myRating?.elo ?: 1000,
                player2_elo_before = opponent.elo
            )) { select() }
            .decodeSingle<Match>()


        // Remove both players from queue
        client.from("match_queue")
            .delete { filter { eq("player_id", userId) } }

        println("Match created: ${created.id}")
        return created
    }

    suspend fun reportMatchResult(matchId: String, won: Boolean): Boolean {
        val userId = client.auth.currentUserOrNull()?.id ?: return false

        val match = client.from("matches")
            .select { filter { eq("id", matchId) } }
            .decodeSingle<Match>()

        val winnerId = if (won) userId else {
            if (match.player1_id == userId) match.player2_id else match.player1_id
        }

        // Calculate new Elo
        val myElo = if (match.player1_id == userId) match.player1_elo_before else match.player2_elo_before
        val opponentElo = if (match.player1_id == userId) match.player2_elo_before else match.player1_elo_before
        val myDeaths = if (match.player1_id == userId) match.player1_deaths else match.player2_deaths
        val opponentDeaths = if (match.player1_id == userId) match.player2_deaths else match.player1_deaths

        val newElos = calculateElo(myElo, opponentElo, won, myDeaths, opponentDeaths)

        // Update match
        client.from("matches")
            .update({
                set("winner_id", winnerId)
                set("status", "completed")
                if (match.player1_id == userId) {
                    set("player1_elo_after", newElos.first)
                    set("player2_elo_after", newElos.second)
                } else {
                    set("player2_elo_after", newElos.first)
                    set("player1_elo_after", newElos.second)
                }
            }) {
                filter { eq("id", matchId) }
            }

        // Update player rating
        updatePlayerRating(userId, newElos.first, won)

        println("Match result: ${if (won) "WIN" else "LOSS"}, new Elo: ${newElos.first}")
        return true
    }

    private fun calculateElo(
        myElo: Int,
        opponentElo: Int,
        won: Boolean,
        myDeaths: Int,
        opponentDeaths: Int
    ): Pair<Int, Int> {
        val k = 32
        val expected = 1.0 / (1.0 + Math.pow(10.0, (opponentElo - myElo) / 400.0))
        val actual = if (won) 1.0 else 0.0

        // Death modifier — fewer deaths = bigger gain
        val deathModifier = (opponentDeaths - myDeaths) * 2
        val adjustedK = (k + deathModifier).coerceIn(16, 64)

        val myNewElo = (myElo + adjustedK * (actual - expected)).toInt()
        val opponentNewElo = (opponentElo + adjustedK * ((1 - actual) - (1 - expected))).toInt()

        return Pair(myNewElo, opponentNewElo)
    }

    suspend fun getPlayerRating(userId: String): PlayerRating? {
        return try {
            client.from("player_ratings")
                .select { filter { eq("player_id", userId) } }
                .decodeSingleOrNull<PlayerRating>()
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun updatePlayerRating(userId: String, newElo: Int, won: Boolean) {
        val existing = getPlayerRating(userId)
        if (existing == null) {
            client.from("player_ratings")
                .insert(PlayerRatingInsert(
                    player_id = userId,
                    elo = newElo,
                    matches_played = 1,
                    wins = if (won) 1 else 0,
                    losses = if (won) 0 else 1
                ))
        } else {
            client.from("player_ratings")
                .update({
                    set("elo", newElo)
                    set("matches_played", existing.matches_played + 1)
                    set("wins", existing.wins + if (won) 1 else 0)
                    set("losses", existing.losses + if (won) 0 else 1)
                }) {
                    filter { eq("player_id", userId) }
                }
        }
    }

    suspend fun getLeaderboard(): List<LeaderboardEntry> {
        return try {
            val ratings = client.from("player_ratings")
                .select()
                .decodeList<PlayerRating>()
                .sortedByDescending { it.elo }
                .take(10)

            ratings.map { rating ->
                val profile = try {
                    client.from("profiles")
                        .select { filter { eq("id", rating.player_id) } }
                        .decodeSingleOrNull<Profile>()
                } catch (e: Exception) { null }

                LeaderboardEntry(
                    player_id = rating.player_id,
                    elo = rating.elo,
                    matches_played = rating.matches_played,
                    wins = rating.wins,
                    losses = rating.losses,
                    username = profile?.username ?: "Unknown"
                )
            }
        } catch (e: Exception) {
            println("Leaderboard error: ${e.message}")
            emptyList()
        }
    }


    suspend fun applyEloDelta(userId: String, delta: Int): Int? {
        return try {
            val response = client.postgrest.rpc(
                function = "apply_elo_delta",
                parameters = buildJsonObject {
                    put("p_user_id", userId)
                    put("p_delta", delta)
                }
            )
            response.data.toIntOrNull()
        } catch (e: Exception) {
            println(" Elo delta error: ${e.message}")
            null
        }
    }

    fun session() = client.auth.currentSessionOrNull()
}