package com.disone.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.disone.core.auth.AuthRepository
import com.disone.core.auth.AuthState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onAddonManagerClick: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val authState by viewModel.authState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            when (val auth = authState) {
                is AuthState.Authenticated -> {
                    ListItem(
                        headlineContent = { Text("Wallet") },
                        supportingContent = { Text(auth.wallet.take(12) + "..." + auth.wallet.takeLast(8)) }
                    )
                    ListItem(
                        headlineContent = { Text("Plan") },
                        supportingContent = { Text(auth.plan) }
                    )
                    TextButton(
                        onClick = { viewModel.signOut() },
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text("Sign Out", color = MaterialTheme.colorScheme.error)
                    }
                }
                else -> {}
            }
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Addon Manager") },
                supportingContent = { Text("Manage Stremio-compatible addons") },
                modifier = Modifier.clickable(onClick = onAddonManagerClick)
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Downloads") },
                supportingContent = { Text("/Android/data/com.disone/files/downloads/") }
            )
            ListItem(
                headlineContent = { Text("Max concurrent torrents") },
                supportingContent = { Text("2") }
            )
            ListItem(
                headlineContent = { Text("WiFi only") },
                supportingContent = { Text("Off") }
            )
            TextButton(
                onClick = { viewModel.clearCache() },
                modifier = Modifier.padding(16.dp)
            ) {
                Text("Clear cache")
            }
        }
    }
}
