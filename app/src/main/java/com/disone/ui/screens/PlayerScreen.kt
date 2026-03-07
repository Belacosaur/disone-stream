package com.disone.ui.screens

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.disone.core.subtitle.SubtitleCue
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.disone.ui.LocalActivityResultSender
import com.disone.ui.components.PurchasePackageSheet
import kotlinx.coroutines.launch
import org.videolan.libvlc.util.VLCVideoLayout

private fun formatTime(ms: Long): String {
    if (ms < 0) return "0:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    magnet: String?,
    fileIndex: Int,
    usePendingStream: Boolean = false,
    onBack: () -> Unit,
    onDiscover: () -> Unit = {},
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val packages by viewModel.packages.collectAsState()
    val packagesLoading by viewModel.packagesLoading.collectAsState()
    val recipientAddress by viewModel.recipientAddress.collectAsState()
    val purchaseInProgress by viewModel.purchaseInProgress.collectAsState()
    val pendingVerification by viewModel.pendingVerification.collectAsState()
    val purchaseConfirmation by viewModel.purchaseConfirmation.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity
    val view = LocalView.current
    val activityResultSender = LocalActivityResultSender.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showControls by remember { mutableStateOf(true) }
    var showPurchaseSheet by remember { mutableStateOf(false) }
    LaunchedEffect(showControls) {
        if (showControls) {
            kotlinx.coroutines.delay(4000)
            showControls = false
        }
    }

    LaunchedEffect(state.isFullscreen) {
        activity?.let { act ->
            val insetsController = WindowInsetsControllerCompat(act.window, view)
            WindowCompat.setDecorFitsSystemWindows(act.window, !state.isFullscreen)
            if (state.isFullscreen) {
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    BackHandler(enabled = state.isFullscreen) {
        viewModel.setFullscreen(false)
    }

    LaunchedEffect(magnet, usePendingStream) {
        if (usePendingStream) {
            viewModel.playFromPending()
        } else if (magnet != null) {
            viewModel.play(magnet, fileIndex)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                showControls = !showControls
            }
    ) {
        AndroidView(
            factory = { ctx ->
                VLCVideoLayout(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { vlcLayout ->
                viewModel.attachVideoView(vlcLayout)
            }
        )

        // Gradient only at top/bottom strips so subtitles remain visible in center
        if (showControls) {
            Box(
                modifier = Modifier.fillMaxSize()
                    .drawBehind {
                        val h = size.height
                        val topBand = h * 0.18f
                        val bottomBand = h * 0.12f
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.7f),
                                    Color.Transparent
                                ),
                                startY = 0f,
                                endY = topBand
                            ),
                            topLeft = Offset.Zero,
                            size = Size(size.width, topBand)
                        )
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.5f)
                                ),
                                startY = 0f,
                                endY = bottomBand
                            ),
                            topLeft = Offset(0f, h - bottomBand),
                            size = Size(size.width, bottomBand)
                        )
                    }
            )
        }

    var optionsSheetOpen by remember { mutableStateOf(false) }

        if (showControls) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = { viewModel.toggleMute() }) {
                    Icon(
                        if (state.isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        contentDescription = if (state.isMuted) "Unmute" else "Mute",
                        tint = Color.White
                    )
                }
                IconButton(onClick = { optionsSheetOpen = true }) {
                    Icon(Icons.Default.Settings, contentDescription = "Player options", tint = Color.White)
                }
            }
        }

        if (optionsSheetOpen) {
            PlayerOptionsSheet(
                subtitleTracks = state.subtitleTracks,
                selectedSubtitleId = state.selectedSubtitleTrackId,
                extraSubtitleTracks = state.extraSubtitleTracks,
                selectedExternalSubtitleUrl = state.selectedExternalSubtitleUrl,
                subtitleDelayMs = state.subtitleDelayMs,
                audioTracks = state.audioTracks,
                selectedAudioId = state.selectedAudioTrackId,
                playbackRate = state.playbackRate,
                hasStreamUrl = state.currentStreamUrl != null,
                onDismiss = { optionsSheetOpen = false },
                onSubtitleSelected = { viewModel.setSubtitleTrack(it) },
                onExternalSubtitleSelected = { viewModel.loadExternalSubtitle(it) },
                onSubtitleDelayChange = { viewModel.setSubtitleDelay(it) },
                onAudioSelected = { viewModel.setAudioTrack(it) },
                onSpeedSelected = { viewModel.setPlaybackRate(it) },
                onCopyStreamLink = {
                    val url = viewModel.getStreamUrlForSharing()
                    if (url != null) {
                        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        cm?.setPrimaryClip(ClipData.newPlainText("stream", url))
                    }
                    optionsSheetOpen = false
                },
                onOpenExternally = {
                    val url = viewModel.getStreamUrlForSharing()
                    if (url != null) {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                            setDataAndType(Uri.parse(url), "video/*")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(Intent.createChooser(intent, "Open with"))
                    }
                    optionsSheetOpen = false
                },
            )
        }

        if (showControls) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                if (state.durationMs > 0) {
                    Slider(
                        value = state.positionMs.toFloat().coerceIn(0f, state.durationMs.toFloat()),
                        onValueChange = { viewModel.seekTo(it.toLong()) },
                        valueRange = 0f..state.durationMs.toFloat().coerceAtLeast(1f),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilledIconButton(
                            onClick = {
                                showControls = true
                                viewModel.togglePlayPause()
                            },
                            modifier = Modifier.size(48.dp),
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color.White.copy(alpha = 0.3f),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (state.isPlaying) "Pause" else "Play",
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Text(
                            "${formatTime(state.positionMs)} / ${formatTime(state.durationMs)}",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    IconButton(
                        onClick = {
                            viewModel.setFullscreen(!state.isFullscreen)
                        }
                    ) {
                        Icon(
                            if (state.isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = if (state.isFullscreen) "Exit fullscreen" else "Fullscreen",
                            tint = Color.White
                        )
                    }
                }
            }
        }

        if (state.selectedExternalSubtitleUrl != null && state.overlaySubtitleCues.isNotEmpty()) {
            val config = LocalConfiguration.current
            val isLandscape = config.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
            val bottomPadding = if (isLandscape) 32.dp else 80.dp
            val posMs = (state.positionMs + state.subtitleDelayMs).coerceAtLeast(0L)
            val currentCue = state.overlaySubtitleCues.find { posMs in it.startMs..it.endMs }
            if (currentCue != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(PaddingValues(start = 24.dp, end = 24.dp, bottom = bottomPadding))
                ) {
                    Text(
                        text = currentCue.text,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            shadow = Shadow(Color.Black, offset = Offset(1f, 1f), blurRadius = 4f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        when {
            state.accessChecking -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Checking access...", color = Color.White)
                }
            }
            state.accessDenied -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.85f))
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        "Subscribe to watch",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        if (state.accessDeniedReason == "expired")
                            "Your free trial has ended. Buy a package to continue watching."
                        else
                            "Subscribe to watch. Seeker device holders get 1 day free.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.9f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            showPurchaseSheet = true
                            viewModel.loadPackages()
                        },
                        modifier = Modifier.height(52.dp)
                    ) {
                        Text("View plans")
                    }
                }
            }
            state.isBuffering -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White
                )
            }
            state.error != null && !state.accessDenied -> {
                Text(
                    state.error!!,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }

    if (showPurchaseSheet) {
        PurchasePackageSheet(
            packages = packages,
            packagesLoading = packagesLoading,
            recipientAddress = recipientAddress,
            pendingVerification = pendingVerification,
            purchaseInProgress = purchaseInProgress,
            onLoadPackages = { viewModel.loadPackages() },
            onPurchase = { pkg ->
                activityResultSender?.let { sender ->
                    viewModel.purchasePackage(sender, pkg) { result, canRetry ->
                        result.onSuccess {
                            showPurchaseSheet = false
                            viewModel.dismissPurchaseConfirmation()
                            viewModel.retryAfterPurchase()
                        }.onFailure { e ->
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    if (canRetry) "Payment sent but verification delayed. Tap Retry."
                                    else (e.message ?: "Purchase failed")
                                )
                            }
                        }
                    }
                } ?: scope.launch { snackbarHostState.showSnackbar("Unable to connect wallet") }
            },
            onRetryVerification = {
                viewModel.retryPendingVerification { result ->
                    result.onSuccess {
                        showPurchaseSheet = false
                        viewModel.dismissPurchaseConfirmation()
                        viewModel.retryAfterPurchase()
                    }.onFailure { e ->
                        scope.launch { snackbarHostState.showSnackbar(e.message ?: "Retry failed") }
                    }
                }
            },
            onClearPendingVerification = { viewModel.clearPendingVerification() },
            onDismiss = { showPurchaseSheet = false },
            canPurchase = !recipientAddress.isNullOrBlank() && activityResultSender != null
        )
    }

    if (purchaseConfirmation != null) {
        AlertDialog(
            onDismissRequest = {
                viewModel.dismissPurchaseConfirmation()
                showPurchaseSheet = false
                viewModel.retryAfterPurchase()
            },
            title = { Text("Payment confirmed") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Your subscription is active. You can now watch.", style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.dismissPurchaseConfirmation()
                    showPurchaseSheet = false
                    viewModel.retryAfterPurchase()
                }) {
                    Text("Watch")
                }
            }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stop()
        }
    }
}
