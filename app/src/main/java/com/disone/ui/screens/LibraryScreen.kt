package com.disone.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.disone.R
import com.disone.core.auth.AuthState
import com.disone.core.models.LibraryItem
import com.disone.core.utils.parseItemId

private val DROPDOWN_BOX_COLOR = Color(0xFF1C1C1C)

@Composable
fun LibraryScreen(
    onItemClick: (String, String) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val authState by viewModel.authState.collectAsState()

    LaunchedEffect(authState) {
        if (authState is AuthState.Authenticated) {
            viewModel.load()
        }
    }

    Scaffold(
        topBar = {},
        containerColor = Color(0xFF0A0A0A)
    ) { padding ->
        when (authState) {
            is AuthState.Authenticated -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    state.error?.let { err ->
                        Text(
                            err,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp)
                        )
                        TextButton(
                            onClick = { viewModel.clearError() },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) {
                            Text("Dismiss")
                        }
                    }

                    when {
                        state.isLoading && state.continueWatching.isEmpty() && state.libraryItems.isEmpty() -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                        state.continueWatching.isEmpty() && state.libraryItems.isEmpty() -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .padding(48.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Image(
                                    painter = painterResource(R.drawable.empty),
                                    contentDescription = null,
                                    modifier = Modifier.size(200.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "Your library is empty",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "Add movies and TV shows from Discovery.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }
                        else -> {
                            LibraryContent(
                                state = state,
                                viewModel = viewModel,
                                onItemClick = onItemClick
                            )
                        }
                    }
                }
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(48.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        painter = painterResource(R.drawable.empty),
                        contentDescription = null,
                        modifier = Modifier.size(200.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Sign in to view your library",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryContent(
    state: LibraryState,
    viewModel: LibraryViewModel,
    onItemClick: (String, String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Filter row: All | Movies | Series
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = state.typeFilter == null,
                onClick = { viewModel.setTypeFilter(null) },
                label = { Text("All") }
            )
            FilterChip(
                selected = state.typeFilter == "movie",
                onClick = { viewModel.setTypeFilter("movie") },
                label = { Text("Movies") }
            )
            FilterChip(
                selected = state.typeFilter == "series",
                onClick = { viewModel.setTypeFilter("series") },
                label = { Text("Series") }
            )
        }

        // Sort row: By Last Watched | By Date Added | By Name | By Release Date
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            sortChip("By Last Watched", "lastWatched", state.sortBy, viewModel::setSort)
            sortChip("By Date Added", "added", state.sortBy, viewModel::setSort)
            sortChip("By Name", "name", state.sortBy, viewModel::setSort)
            sortChip("By Release", "released", state.sortBy, viewModel::setSort)
        }

        if (state.continueWatching.isNotEmpty()) {
            Text(
                "Continue Watching",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = Color.White
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.height(180.dp)
            ) {
                items(state.continueWatching) { item ->
                    LibraryItemCard(
                        item = item,
                        onClick = {
                            val (type, id) = parseItemId(item.id)
                            onItemClick(type, id)
                        },
                        modifier = Modifier.width(110.dp),
                        showProgress = true
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 120.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(state.libraryItems) { item ->
                LibraryItemCard(
                    item = item,
                    onClick = {
                        val (type, id) = parseItemId(item.id)
                        onItemClick(type, id)
                    },
                    showProgress = true
                )
            }
        }
    }
}

@Composable
private fun sortChip(
    label: String,
    sortKey: String,
    currentSort: String,
    onSort: (String) -> Unit
) {
    FilterChip(
        selected = currentSort == sortKey,
        onClick = { onSort(sortKey) },
        label = { Text(label) }
    )
}

@Composable
private fun LibraryItemCard(
    item: LibraryItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showProgress: Boolean = true
) {
    val isWatched = item.progress >= 99.9 || item.state?.flaggedWatched == 1

    Card(
        modifier = modifier
            .aspectRatio(0.67f)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = DROPDOWN_BOX_COLOR),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = item.poster,
                contentDescription = item.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                placeholder = painterResource(R.drawable.libraryplaceholder),
                error = painterResource(R.drawable.libraryplaceholder)
            )
            if (isWatched) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.9f), RoundedCornerShape(4.dp))
                        .padding(4.dp)
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Watched",
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            if (showProgress && item.progress > 0 && item.progress < 99.9) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(4.dp)
                        .height(4.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(2.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((item.progress / 100).toFloat().coerceIn(0f, 1f))
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                    )
                }
            }
        }
    }
}
