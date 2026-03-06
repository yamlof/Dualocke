package org.example.project.ui.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun QuickActionGrid(
    onLeaderBoardsClick:() ->Unit,
    onHistoryClick:() -> Unit,
    onCommunityClick:() -> Unit,
    onSettingsClick: () -> Unit
){
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ){
        Row (
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ){
            QuickActionCard(
                title = "LeaderBoards",
                icon = Icons.Default.List,
                description = "View Rankings",
                modifier = Modifier.weight(1f),
                onClick = onLeaderBoardsClick
            )
            QuickActionCard(
                title = "Match History",
                icon = Icons.Default.Done,
                description = "Past battles",
                modifier = Modifier.weight(1f),
                onClick = onHistoryClick
            )
            Row (
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ){
                QuickActionCard(
                    title = "Community",
                    icon = Icons.Default.Person,
                    description = "Connect",
                    modifier = Modifier.weight(1f),
                    onClick = onCommunityClick
                )
                QuickActionCard(
                    title = "Settings",
                    icon = Icons.Default.Settings,
                    description = "Preferences",
                    modifier = Modifier.weight(1f),
                    onClick = onSettingsClick
                )
            }


        }
    }
}