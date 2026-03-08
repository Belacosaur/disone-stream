package com.disone.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddonManagerScreen(
    onBack: () -> Unit,
    viewModel: AddonManagerViewModel = hiltViewModel()
) {
    val addons by viewModel.installedAddons.collectAsState(initial = emptyList())
    val addUrlDialog by viewModel.addUrlDialogState.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Addon Manager", style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = { viewModel.showAddUrlDialog() }) {
                    Icon(Icons.Default.Add, contentDescription = "Add addon")
                }
            }
            Text(
                "Stremio-compatible addons provide catalogs and streams. Add addon URLs to browse content.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
            if (errorMessage != null) {
                Card(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        errorMessage!!,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            if (addons.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No addons installed")
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { viewModel.showAddUrlDialog() }) {
                            Text("Add your first addon")
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(addons) { addon ->
                        AddonListItem(
                            addon = addon,
                            onToggle = { viewModel.setEnabled(addon.url, !addon.enabled) },
                            onRemove = { viewModel.removeAddon(addon.url) }
                        )
                    }
                }
            }
        }
    }

    if (addUrlDialog) {
        var input by remember { mutableStateOf("") }
        LaunchedEffect(addUrlDialog) { if (addUrlDialog) input = "" }
        AlertDialog(
            onDismissRequest = { viewModel.dismissAddUrlDialog() },
            title = { Text("Add Stremio Addon") },
            text = {
                Column {
                    Text(
                        "Enter addon URL (e.g. https://v3-cinemeta.strem.io or https://some-addon.com/manifest.json)",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("https://...") }
                    )
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.addAddon(input) }) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissAddUrlDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun AddonListItem(
    addon: com.disone.core.addons.InstalledAddon,
    onToggle: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    addon.manifest.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    addon.manifest.version,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    addon.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row {
                Switch(
                    checked = addon.enabled,
                    onCheckedChange = { onToggle() }
                )
                TextButton(onClick = onRemove) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
