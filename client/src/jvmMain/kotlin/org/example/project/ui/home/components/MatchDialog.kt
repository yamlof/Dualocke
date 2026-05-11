package org.example.project.ui.home.components

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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import org.example.project.Match
import org.example.project.domain.models.PokemonTeamMember


@Composable
fun MatchDialog(
    match: Match,
    showdownUsername: String,
    onUsernameChange: (String) -> Unit,
    showdownUrl: String?,
    onStartBattle: () -> Unit,
    onWin: () -> Unit,
    onLoss: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())  // dialog can get tall
            ) {
                // Header
                Text(
                    "⚔️ Match Found!",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Badge ${match.badge_count} · Gen 1 Nuzlocke",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Teams side by side
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Your Team",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        val myTeam = Json.decodeFromJsonElement<List<PokemonTeamMember>>(match.player1_team)
                        myTeam.forEach { mon -> TeamMonRow(mon = mon) }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Deaths: ${match.player1_deaths}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    VerticalDivider(modifier = Modifier.fillMaxHeight(0.8f))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Opponent",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        val opponentTeam = Json.decodeFromJsonElement<List<PokemonTeamMember>>(match.player2_team)
                        opponentTeam.forEach { mon -> TeamMonRow(mon = mon) }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Deaths: ${match.player2_deaths}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Showdown setup section — visible until the bot creates the room
                if (showdownUrl == null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Setup",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "1. Copy your team below\n" +
                                        "2. Open Showdown and log in with the username you'll use\n" +
                                        "3. Enter that same username here\n" +
                                        "4. Click Start Battle — the bot will challenge you",
                                style = MaterialTheme.typography.bodySmall
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = {
                                    val myTeam = Json.decodeFromJsonElement<List<PokemonTeamMember>>(match.player1_team)
                                    val exportString = generateShowdownExport(myTeam)
                                    val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                                    clipboard.setContents(java.awt.datatransfer.StringSelection(exportString), null)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary
                                )
                            ) {
                                Text("📋 Copy My Team for Showdown")
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedButton(
                                onClick = {
                                    java.awt.Desktop.getDesktop()
                                        .browse(java.net.URI("http://localhost:8000/"))
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("🌐 Open Showdown")
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = showdownUsername,
                                onValueChange = onUsernameChange,
                                label = { Text("Your Showdown username") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = onStartBattle,
                                enabled = showdownUsername.isNotBlank(),
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text("⚔️ Start Battle", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    // Battle is live
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("✅ Battle Room Ready!", fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Accept the challenge from the bot in Showdown.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                showdownUrl,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    java.awt.Desktop.getDesktop()
                                        .browse(java.net.URI(showdownUrl))
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("🎮 Open Battle in Browser")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Manual fallback result buttons
                Text(
                    "Manual result (auto-detection should handle this normally):",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onWin,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("🏆 I Won")
                    }
                    OutlinedButton(
                        onClick = onLoss,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("💀 I Lost")
                    }
                }
            }
        }
    }
}

@Composable
private fun TeamMonRow(mon: PokemonTeamMember) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/versions/generation-vii/icons/${getPokemonId(mon.species)}.png",
            contentDescription = null,
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                mon.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Lv${mon.level}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun getPokemonId(species: String): Int {
    val ids = mapOf(
        "bulbasaur" to 1, "ivysaur" to 2, "venusaur" to 3,
        "charmander" to 4, "charmeleon" to 5, "charizard" to 6,
        "squirtle" to 7, "wartortle" to 8, "blastoise" to 9,
        "caterpie" to 10, "metapod" to 11, "butterfree" to 12,
        "weedle" to 13, "kakuna" to 14, "beedrill" to 15,
        "pidgey" to 16, "pidgeotto" to 17, "pidgeot" to 18,
        "rattata" to 19, "raticate" to 20, "spearow" to 21,
        "fearow" to 22, "ekans" to 23, "arbok" to 24,
        "pikachu" to 25, "raichu" to 26, "sandshrew" to 27,
        "sandslash" to 28, "nidoran-f" to 29, "nidorina" to 30,
        "nidoqueen" to 31, "nidoran-m" to 32, "nidorino" to 33,
        "nidoking" to 34, "clefairy" to 35, "clefable" to 36,
        "vulpix" to 37, "ninetales" to 38, "jigglypuff" to 39,
        "wigglytuff" to 40, "zubat" to 41, "golbat" to 42,
        "oddish" to 43, "gloom" to 44, "vileplume" to 45,
        "paras" to 46, "parasect" to 47, "venonat" to 48,
        "venomoth" to 49, "diglett" to 50
    )
    return ids[species.lowercase()] ?: 1
}

private fun generateShowdownExport(team: List<PokemonTeamMember>): String {
    return team.joinToString("\n\n") { mon ->
        """${mon.name} (${mon.species.replaceFirstChar { it.uppercase() }})
Level: ${mon.level}
- Tackle
- Growl
- Leer
- Scratch"""
    }
}