package com.disone.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.disone.core.auth.AuthState
import com.disone.core.models.Comment
import com.disone.core.models.Review
import androidx.compose.material3.ExperimentalMaterial3Api
import com.disone.ui.components.RatingSlider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TitleDetailScreen(
    type: String,
    id: String,
    onWatch: () -> Unit,
    onBack: () -> Unit,
    viewModel: TitleDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val authState by viewModel.authState.collectAsState()

    LaunchedEffect(type, id) {
        viewModel.load(type, id)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        when {
            state.isLoading && state.meta == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            state.error != null && state.meta == null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(state.error!!, color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { viewModel.load(type, id) }) {
                        Text("Retry")
                    }
                }
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                ) {
                    state.meta?.let { meta ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            AsyncImage(
                                model = meta.poster,
                                contentDescription = meta.name,
                                modifier = Modifier
                                    .size(width = 120.dp, height = 180.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentScale = ContentScale.Crop,
                                placeholder = painterResource(R.drawable.libraryplaceholder),
                                error = painterResource(R.drawable.libraryplaceholder)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = meta.name,
                                    style = MaterialTheme.typography.headlineSmall
                                )
                                meta.year?.let { Text("$it", style = MaterialTheme.typography.bodyMedium) }
                                meta.imdbRating?.let {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("IMDb: $it", style = MaterialTheme.typography.bodyMedium)
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    FilledTonalButton(onClick = onWatch) {
                                        Text("Watch")
                                    }
                                    if (authState is AuthState.Authenticated) {
                                        if (state.inLibrary) {
                                            FilledTonalButton(
                                                onClick = { viewModel.removeFromLibrary() },
                                                modifier = Modifier.size(48.dp),
                                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Bookmark,
                                                    contentDescription = "Remove from Library",
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        } else {
                                            OutlinedButton(
                                                onClick = { viewModel.addToLibrary() },
                                                modifier = Modifier.size(48.dp),
                                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                                            ) {
                                                Icon(
                                                    Icons.AutoMirrored.Filled.PlaylistAdd,
                                                    contentDescription = "Add to Library",
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        meta.description?.let { desc ->
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }

                    state.error?.let { err ->
                        Text(err, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                        Button(
                            onClick = { viewModel.clearError() },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) {
                            Text("Dismiss")
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                    state.ratings?.let { ratings ->
                        RatingSlider(
                            averageRating = ratings.averageRating.toDouble(),
                            totalRatings = ratings.totalRatings,
                            userRating = ratings.userRating,
                            isAuthenticated = authState is AuthState.Authenticated,
                            onSubmitRating = { viewModel.submitRating(type, id, it) },
                            onClear = if (authState is AuthState.Authenticated) {{ viewModel.deleteRating(type, id) }} else null,
                            modifier = Modifier.padding(16.dp)
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                    Text("Reviews", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
                    if (authState is AuthState.Authenticated) {
                        var reviewText by remember { mutableStateOf("") }
                        var reviewRating by remember { mutableStateOf<Int?>(null) }
                        OutlinedTextField(
                            value = reviewText,
                            onValueChange = { if (it.length <= 560) reviewText = it },
                            label = { Text("Write a review (max 560 chars)") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            maxLines = 3
                        )
                        Button(
                            onClick = {
                                if (reviewText.isNotBlank()) {
                                    viewModel.submitReview(type, id, reviewText.trim(), reviewRating)
                                    reviewText = ""
                                    reviewRating = null
                                }
                            },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) {
                            Text("Submit Review")
                        }
                    }
                    state.reviews.forEach { review ->
                        ReviewCard(
                            review = review,
                            showActions = authState is AuthState.Authenticated,
                            onDelete = { viewModel.deleteReview(type, id, review.id) }
                        )
                    }
                    state.reviewsPagination?.let { pag ->
                        if (pag.page < pag.totalPages) {
                            Button(
                                onClick = { viewModel.loadMoreReviews(type, id) },
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text("Load more reviews")
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                    Text("Comments", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
                    if (authState is AuthState.Authenticated) {
                        var commentText by remember { mutableStateOf("") }
                        OutlinedTextField(
                            value = commentText,
                            onValueChange = { commentText = it },
                            label = { Text("Add a comment") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            maxLines = 2
                        )
                        Button(
                            onClick = {
                                if (commentText.isNotBlank()) {
                                    viewModel.submitComment(type, id, commentText.trim(), null)
                                    commentText = ""
                                }
                            },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) {
                            Text("Submit Comment")
                        }
                    }
                    state.comments.forEach { comment ->
                        CommentCard(comment = comment)
                    }
                    state.commentsPagination?.let { pag ->
                        if (pag.page < pag.totalPages) {
                            Button(
                                onClick = { viewModel.loadMoreComments(type, id) },
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text("Load more comments")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewCard(
    review: Review,
    showActions: Boolean,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(review.user.username, style = MaterialTheme.typography.labelMedium)
                review.rating?.let { Text("${it / 10f}", style = MaterialTheme.typography.bodySmall) }
            }
            Text(review.body, style = MaterialTheme.typography.bodyMedium)
            if (showActions) {
                Row(modifier = Modifier.padding(top = 8.dp)) {
                    Button(onClick = onDelete) { Text("Delete") }
                }
            }
        }
    }
}

@Composable
private fun CommentCard(comment: Comment) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(comment.user.username, style = MaterialTheme.typography.labelMedium)
            Text(comment.body, style = MaterialTheme.typography.bodyMedium)
            comment.replies.forEach { reply ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(reply.user.username, style = MaterialTheme.typography.labelSmall)
                Text(reply.body, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
