package org.example.project.ui.home.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.example.project.domain.models.GameVersion
import java.io.File
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.Color
import org.example.project.utils.FilePicker
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme

@Composable
fun GameLibraryDialog(
    onDismiss: () -> Unit,
    onGameSelected: (GameVersion) -> Unit,
    onVerifyRom: (GameVersion, File) -> Unit,
    onSwitchSlot: (GameVersion, String) -> Unit,
    onCreateSlot: (GameVersion, String) -> Unit,
    onDeleteSlot: (GameVersion, String) -> Unit,
    verifiedGames: Set<GameVersion>,
    activeGame: GameVersion?,
    activeSlot: String?,
    slotsByGame: Map<GameVersion, List<String>>
) {
    var selectedGame by remember { mutableStateOf<GameVersion?>(activeGame) }
    var showDeleteWarning by remember { mutableStateOf<Pair<GameVersion, String>?>(null) }

    if (showDeleteWarning != null) {
        AlertDialog(
            onDismissRequest = { showDeleteWarning = null },
            title = { Text("Delete Run?") },
            text = {
                Text("Deleting this run will permanently remove your save data. Your Elo rating will decrease by 50 points as a penalty for abandoning a run. This cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteSlot(showDeleteWarning!!.first, showDeleteWarning!!.second)
                        showDeleteWarning = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete & Accept Penalty")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteWarning = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    "Game Library",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (selectedGame == null) {
                    // Game list
                    GameVersion.entries.forEach { game ->
                        val isVerified = game in verifiedGames
                        GameLibraryItem(
                            game = game,
                            isVerified = isVerified,
                            isActive = game == activeGame,
                            onVerifyRom = { file -> onVerifyRom(game, file) },
                            onClick = {
                                if (isVerified) selectedGame = game
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                } else {

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { selectedGame = null }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                        Text(
                            selectedGame!!.getDisplayName(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))


                    SaveSlotList(
                        gameVersion = selectedGame!!,
                        slots = slotsByGame[selectedGame] ?: emptyList(),
                        activeSlot = if (activeGame == selectedGame) activeSlot else null,
                        isSwitching = false,
                        onSwitchSlot = { slot ->
                            onGameSelected(selectedGame!!)
                            onSwitchSlot(selectedGame!!, slot)
                            onDismiss()
                        },
                        onCreateSlot = { name -> onCreateSlot(selectedGame!!, name) },
                        onDeleteSlot = { name ->
                            showDeleteWarning = Pair(selectedGame!!, name)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun GameLibraryItem(
    game: GameVersion,
    isVerified: Boolean,
    isActive: Boolean,
    onVerifyRom: (File) -> Unit,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isActive)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isVerified) { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    game.getDisplayName(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (isVerified) "Verified" else "ROM not verified",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isVerified)
                        Color(0xFF4CAF50)
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!isVerified) {
                Button(
                    onClick = {
                        FilePicker.pickRomFile { file ->
                            onVerifyRom(file)
                        }
                    }
                ) {
                    Text("Verify ROM")
                }
            } else if (isActive) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun SaveSlotList(
    gameVersion: GameVersion,
    slots: List<String>,
    activeSlot: String?,
    isSwitching: Boolean,
    onSwitchSlot: (String) -> Unit,
    onCreateSlot: (String) -> Unit,
    onDeleteSlot: (String) -> Unit
) {
    var showCreateSlot by remember { mutableStateOf(false) }
    var newSlotName by remember { mutableStateOf("") }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Save Slots", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        IconButton(onClick = { showCreateSlot = !showCreateSlot }) {
            Icon(Icons.Default.Add, contentDescription = "New slot")
        }
    }

    if (showCreateSlot) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = newSlotName,
                onValueChange = { newSlotName = it },
                placeholder = { Text("Slot name...") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Button(onClick = {
                if (newSlotName.isNotBlank()) {
                    onCreateSlot(newSlotName.trim())
                    newSlotName = ""
                    showCreateSlot = false
                }
            }) { Text("Create") }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }

    if (slots.isEmpty()) {
        Text(
            "No save slots yet. Create one to start playing.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    slots.forEach { slot ->
        val isActive = slot == activeSlot
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isActive)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clickable(enabled = !isSwitching) { onSwitchSlot(slot) }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    slot,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isActive) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    IconButton(
                        onClick = { onDeleteSlot(slot) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null,
                            tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}