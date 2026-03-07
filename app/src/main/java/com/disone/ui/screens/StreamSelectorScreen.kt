package com.disone.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.disone.ui.components.PlanBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamSelectorScreen(
    itemId: String,
    onPlayStream: (com.disone.core.addons.DisoneStream) -> Unit,
    onBack: () -> Unit,
    onDiscover: () -> Unit,
    viewModel: StreamSelectorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var menuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(itemId) {
        viewModel.loadStreams(itemId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select Stream") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Discovery") },
                            onClick = {
                                menuExpanded = false
                                onDiscover()
                            }
                        )
                    }
                },
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
        ) {
            when {
                state.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                state.streams.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No streams available\n\nMake sure Torrentio addon is installed in Settings → Addons.",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.streams) { stream ->
                            stream.disoneStream?.let { ds ->
                                StreamOptionCard(
                                    stream = stream,
                                    onPlay = { if (!stream.requiresPremium) onPlayStream(ds) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (state.magnetDialogOpen) {
        var input by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { viewModel.dismissMagnetDialog() },
            title = { Text("Enter magnet link") },
            text = {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.setManualMagnet(input)
                    input = ""
                }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissMagnetDialog() }) { Text("Cancel") }
            }
        )
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return ""
    return when {
        bytes >= 1024 * 1024 * 1024 -> String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024))
        bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
        bytes >= 1024 -> String.format("%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}

@Composable
private fun StreamOptionCard(
    stream: StreamOptionUi,
    onPlay: () -> Unit
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
            Column {
                Text(stream.resolution, style = MaterialTheme.typography.titleMedium)
                val sizeStr = formatSize(stream.sizeBytes)
                val seedStr = if (stream.seeders > 0) "${stream.seeders} seeders" else null
                Text(
                    listOfNotNull(sizeStr, seedStr).joinToString(" • ").ifEmpty { "—" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PlanBadge(plan = stream.mode)
                    if (stream.requiresPremium) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                "Premium Required",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
            Button(
                onClick = onPlay,
                enabled = !stream.requiresPremium
            ) {
                Text("Play")
            }
        }
    }
}
