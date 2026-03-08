package com.disone.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit = {},
    onAddonManagerClick: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            ListItem(
                headlineContent = { Text("Addon Manager") },
                supportingContent = { Text("Manage Stremio-compatible addons") },
                modifier = Modifier.clickable(onClick = onAddonManagerClick)
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Downloads") },
                supportingContent = { Text("/Android/data/com.disone.app/files/downloads/") }
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
