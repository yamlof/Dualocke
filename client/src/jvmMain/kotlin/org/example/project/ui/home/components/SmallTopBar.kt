package org.example.project.ui.home.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmallTopAppBar(
    onLogout: () -> Unit
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.primary,
        ),
        title = {
            Text(text = "Nuzlocke League", style = MaterialTheme.typography.headlineMedium)
        },
        actions = {

            TextButton(onClick = {}) {
                Text("Profile", color = MaterialTheme.colorScheme.primary)
            }

            TextButton(onClick = onLogout){
                Text("Logout", color = MaterialTheme.colorScheme.primary)
            }
        }
    )
}