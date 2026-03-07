package com.disone.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.disone.R
import com.disone.core.models.CatalogItem

private val DROPDOWN_BOX_COLOR = Color(0xFF1C1C1C)
private val DROPDOWN_BORDER_COLOR = Color(0xFF2A2A2A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoveryScreen(
    onItemClick: (CatalogItem) -> Unit,
    viewModel: DiscoveryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {},
        containerColor = Color(0xFF0A0A0A)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            DiscoveryFilterBar(
                type = state.type,
                catalogName = state.catalogName,
                catalogs = state.catalogs.filter { it.type == state.type },
                genre = state.genre,
                genreOptions = state.genreOptions,
                genreRequired = state.genreRequired,
                onTypeChange = { viewModel.setType(it) },
                onCatalogChange = { c -> viewModel.setCatalog(c.id, c.name) },
                onGenreChange = { viewModel.setGenre(it) }
            )

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.isLoading && state.items.isEmpty() -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    state.error != null -> {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = state.error!!,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { viewModel.loadCatalog() }) {
                                Text("Retry")
                            }
                        }
                    }
                    state.items.isEmpty() -> {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No titles in this category",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { viewModel.loadCatalog() }) {
                                Text("Retry")
                            }
                        }
                    }
                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            contentPadding = PaddingValues(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(state.items) { item ->
                                CatalogItemCard(
                                    item = item,
                                    onClick = { onItemClick(item) }
                                )
                            }
                            if (state.hasNextPage && state.items.isNotEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Button(onClick = { viewModel.loadMore() }) {
                                            Text("Load more")
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
}

private val DROPDOWN_PILL_HEIGHT = 44.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiscoveryFilterBar(
    type: String,
    catalogName: String,
    catalogs: List<com.disone.core.addons.AddonCatalog>,
    genre: String?,
    genreOptions: List<String>,
    genreRequired: Boolean,
    onTypeChange: (String) -> Unit,
    onCatalogChange: (com.disone.core.addons.AddonCatalog) -> Unit,
    onGenreChange: (String?) -> Unit
) {
    var typeExpanded by remember { mutableStateOf(false) }
    var catalogExpanded by remember { mutableStateOf(false) }
    var genreExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        pillDropdown(
            value = type.replaceFirstChar { it.uppercase() },
            expanded = typeExpanded,
            onExpandedChange = { typeExpanded = it },
            modifier = Modifier.widthIn(min = 0.dp).weight(1f)
        ) {
            listOf("movie", "series").forEach { t ->
                DropdownMenuItem(
                    text = { Text(t.replaceFirstChar { it.uppercase() }) },
                    onClick = {
                        onTypeChange(t)
                        typeExpanded = false
                    }
                )
            }
        }

        pillDropdown(
            value = catalogName,
            expanded = catalogExpanded,
            onExpandedChange = { catalogExpanded = it },
            modifier = Modifier.widthIn(min = 0.dp).weight(1f)
        ) {
            for (catalog in catalogs) {
                DropdownMenuItem(
                    text = { Text(catalog.name) },
                    onClick = {
                        onCatalogChange(catalog)
                        catalogExpanded = false
                    }
                )
            }
        }

        if (genreOptions.isNotEmpty()) {
            pillDropdown(
                value = genre ?: if (genreRequired) genreOptions.firstOrNull().orEmpty() else "All",
                expanded = genreExpanded,
                onExpandedChange = { genreExpanded = it },
                modifier = Modifier.widthIn(min = 0.dp).weight(1f)
            ) {
                if (!genreRequired) {
                    DropdownMenuItem(
                        text = { Text("All") },
                        onClick = {
                            onGenreChange(null)
                            genreExpanded = false
                        }
                    )
                }
                for (g in genreOptions) {
                    DropdownMenuItem(
                        text = { Text(g) },
                        onClick = {
                            onGenreChange(g)
                            genreExpanded = false
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun pillDropdown(
    value: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    ExposedDropdownMenuBox(
        modifier = modifier,
        expanded = expanded,
        onExpandedChange = onExpandedChange
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .height(DROPDOWN_PILL_HEIGHT)
                .menuAnchor(),
            textStyle = MaterialTheme.typography.bodySmall,
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                focusedContainerColor = DROPDOWN_BOX_COLOR,
                unfocusedContainerColor = DROPDOWN_BOX_COLOR,
                disabledContainerColor = DROPDOWN_BOX_COLOR
            ),
            shape = RoundedCornerShape(20.dp)
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(DROPDOWN_BOX_COLOR)
        ) {
            content()
        }
    }
}

@Composable
private fun CatalogItemCard(
    item: CatalogItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.67f)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = DROPDOWN_BOX_COLOR),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        AsyncImage(
            model = item.poster,
            contentDescription = item.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            placeholder = painterResource(R.drawable.libraryplaceholder),
            error = painterResource(R.drawable.libraryplaceholder)
        )
    }
}
