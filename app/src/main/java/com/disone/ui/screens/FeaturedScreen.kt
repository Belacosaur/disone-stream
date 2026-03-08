package com.disone.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.disone.R
import com.disone.core.auth.AuthState
import com.disone.core.models.CatalogItem
import com.disone.ui.components.CarouselRow
import com.disone.ui.components.ContinueWatchingCarouselRow
import com.disone.core.utils.parseItemId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeaturedScreen(
    onItemClick: (String, String) -> Unit,
    viewModel: FeaturedViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val authState by viewModel.authState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    Scaffold(
        containerColor = Color(0xFF0A0A0A)
    ) { padding ->
        when {
            state.isLoading && state.rows.isEmpty() && state.continueWatching.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    androidx.compose.material3.CircularProgressIndicator()
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = state.type == "movie",
                                onClick = { viewModel.setType("movie") },
                                label = { Text("Movies") }
                            )
                            FilterChip(
                                selected = state.type == "series",
                                onClick = { viewModel.setType("series") },
                                label = { Text("Series") }
                            )
                        }
                    }
                    item {
                        if (authState is AuthState.Authenticated) {
                            if (state.continueWatching.isNotEmpty()) {
                                ContinueWatchingCarouselRow(
                                    title = "Continue Watching",
                                    items = state.continueWatching,
                                    onItemClick = onItemClick,
                                    onClearProgress = { viewModel.clearProgress(it) }
                                )
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        "Continue Watching",
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "Add titles from Discovery to continue watching here.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            }
                        }
                    }

                    items(state.rows) { row ->
                        CarouselRow(
                            title = row.title,
                            items = row.items,
                            onItemClick = { item ->
                                val (type, id) = parseItemId(item.id)
                                onItemClick(type, id)
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (state.rows.isEmpty() && state.continueWatching.isEmpty() && !state.isLoading) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(48.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Image(
                                    painter = painterResource(R.drawable.empty),
                                    contentDescription = null,
                                    modifier = Modifier.size(120.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "No titles in this category",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
