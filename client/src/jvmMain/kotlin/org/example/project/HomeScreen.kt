package org.example.project

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmallTopAppBarExample(
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

        @Composable
fun HomeScreen(onLogout:() -> Unit){

    Column {
        SmallTopAppBarExample(onLogout = onLogout)

        Text(
            "Welcome to your Nuzlocke League!",
            modifier = Modifier.padding(24.dp),
            style = MaterialTheme.typography.bodyLarge
        )
        Button(
            onClick = {
                lauchMGBA()
            }
        ){
            Text("Start")
        }
    }

}