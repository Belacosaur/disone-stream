package com.disone.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.disone.R
import com.disone.core.addons.AddonVideo

private fun episodeThumbnailUrl(seriesId: String, season: Int?, episodeNum: Int?): String? {
    val s = season ?: 1
    val e = episodeNum ?: return null
    return "https://episodes.metahub.space/$seriesId/$s/$e/w780.jpg"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodesScreen(
    itemId: String,
    onPlayStream: (com.disone.core.addons.DisoneStream, resumeFromMs: Long) -> Unit,
    onBack: () -> Unit,
    viewModel: EpisodesViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(itemId) {
        viewModel.loadMeta(itemId)
    }

    val seriesId = state.meta?.id ?: itemId.removePrefix("series:")

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(state.meta?.name ?: "Episodes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        when {
            state.isMetaLoading && state.meta == null -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            state.meta == null && !state.isMetaLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No episodes found", style = MaterialTheme.typography.bodyLarge)
                }
            }
            state.showStreams -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.backToEpisodes() },
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Back to episodes")
                    }
                    when {
                        state.isStreamsLoading -> {
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
                                    "No streams available for this episode.\n\nMake sure Torrentio addon is installed in Settings → Addons.",
                                    style = MaterialTheme.typography.bodyMedium,
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
                                        val resumeMs = if (state.selectedEpisode?.id == state.resumeVideoId) state.savedProgressMs else 0L
                                        StreamOptionCard(
                                            stream = stream,
                                            onPlay = { if (!stream.requiresPremium) onPlayStream(ds, resumeMs) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        val episode = state.selectedEpisode ?: state.episodes.firstOrNull()
                        val thumbUrl = episode?.thumbnail
                            ?: episode?.let { episodeThumbnailUrl(seriesId, it.season, it.episode ?: it.number) }
                            ?: state.meta?.poster
                        val synopsis = episode?.synopsis ?: state.meta?.description

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            AsyncImage(
                                model = thumbUrl,
                                contentDescription = episode?.displayTitle,
                                modifier = Modifier
                                    .size(width = 140.dp, height = 79.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentScale = ContentScale.Crop,
                                placeholder = painterResource(R.drawable.libraryplaceholder),
                                error = painterResource(R.drawable.libraryplaceholder)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    episode?.displayTitle ?: "Select an episode",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                synopsis?.let { txt ->
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        txt,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 4
                                    )
                                }
                            }
                        }
                    }

                    item {
                        var expanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = it }
                        ) {
                            androidx.compose.material3.OutlinedTextField(
                                value = "Season ${state.selectedSeason}",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Season") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                state.seasons.forEach { season ->
                                    DropdownMenuItem(
                                        text = { Text("Season $season") },
                                        onClick = {
                                            viewModel.selectSeason(season)
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    items(state.episodes) { episode ->
                        val thumbUrl = episode.thumbnail
                            ?: episodeThumbnailUrl(seriesId, episode.season, episode.episode ?: episode.number)

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.selectEpisode(episode)
                                    viewModel.loadStreamsForEpisode(episode)
                                },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = thumbUrl,
                                    contentDescription = episode.displayTitle,
                                    modifier = Modifier
                                        .size(width = 160.dp, height = 90.dp)
                                        .background(MaterialTheme.colorScheme.surface),
                                    contentScale = ContentScale.Crop,
                                    placeholder = painterResource(R.drawable.libraryplaceholder),
                                    error = painterResource(R.drawable.libraryplaceholder)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        episode.displayTitle,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    episode.synopsis?.let { txt ->
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            txt,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
