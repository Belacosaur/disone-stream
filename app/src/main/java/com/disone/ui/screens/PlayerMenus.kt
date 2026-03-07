package com.disone.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import com.disone.core.player.AudioTrack
import com.disone.core.player.SubtitleTrack

private val SPEED_OPTIONS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerOptionsSheet(
    subtitleTracks: List<SubtitleTrack>,
    selectedSubtitleId: Int,
    extraSubtitleTracks: List<SubtitleTrack>,
    selectedExternalSubtitleUrl: String?,
    subtitleDelayMs: Long,
    audioTracks: List<AudioTrack>,
    selectedAudioId: Int,
    playbackRate: Float,
    hasStreamUrl: Boolean,
    onDismiss: () -> Unit,
    onSubtitleSelected: (Int) -> Unit,
    onExternalSubtitleSelected: (String) -> Unit,
    onSubtitleDelayChange: (Long) -> Unit,
    onAudioSelected: (Int) -> Unit,
    onSpeedSelected: (Float) -> Unit,
    onCopyStreamLink: () -> Unit,
    onOpenExternally: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                "Player Options",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            SubtitlesSection(
                embeddedTracks = subtitleTracks,
                selectedEmbeddedId = selectedSubtitleId,
                extraTracks = extraSubtitleTracks,
                selectedExternalUrl = selectedExternalSubtitleUrl,
                delayMs = subtitleDelayMs,
                onEmbeddedSelected = onSubtitleSelected,
                onExternalSelected = onExternalSubtitleSelected,
                onDelayChange = onSubtitleDelayChange,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            AudioSection(
                tracks = audioTracks,
                selectedId = selectedAudioId,
                onTrackSelected = onAudioSelected,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            SpeedSection(
                currentRate = playbackRate,
                onRateSelected = onSpeedSelected,
            )

            if (hasStreamUrl) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                OptionsSection(
                    onCopyLink = onCopyStreamLink,
                    onOpenExternally = onOpenExternally,
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SubtitlesSection(
    embeddedTracks: List<SubtitleTrack>,
    selectedEmbeddedId: Int,
    extraTracks: List<SubtitleTrack>,
    selectedExternalUrl: String?,
    delayMs: Long,
    onEmbeddedSelected: (Int) -> Unit,
    onExternalSelected: (String) -> Unit,
    onDelayChange: (Long) -> Unit,
) {
    Text("Subtitles", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))

    val nothingSelected = selectedEmbeddedId == -1 && selectedExternalUrl == null

    // Group extra tracks by language for Stremio V4 two-column layout
    val extraByLang = extraTracks.groupBy { it.lang ?: "und" }
    val langLabels = mapOf(
        "eng" to "English", "spa" to "Spanish", "fre" to "French", "fra" to "French",
        "ger" to "German", "deu" to "German", "por" to "Portuguese", "pob" to "Portuguese",
        "ita" to "Italian", "rus" to "Russian", "jpn" to "Japanese", "kor" to "Korean",
        "chi" to "Chinese", "zho" to "Chinese", "ara" to "Arabic", "hin" to "Hindi", "hi" to "Hindi",
        "tur" to "Turkish", "pol" to "Polish", "ukr" to "Ukrainian", "nld" to "Dutch",
        "swe" to "Swedish", "dan" to "Danish", "nor" to "Norwegian", "fin" to "Finnish",
        "ell" to "Greek", "gre" to "Greek", "hun" to "Hungarian", "ron" to "Romanian",
        "ces" to "Czech", "bul" to "Bulgarian", "hrv" to "Croatian", "srp" to "Serbian",
        "slk" to "Slovak", "slv" to "Slovenian", "vie" to "Vietnamese", "tha" to "Thai",
        "und" to "Unknown"
    )
    val leftItems = buildList {
        add(LeftItem.Off)
        // Embedded tracks hidden: libVLC Android has known subtitle rendering issues with TextureView
        val langItems = extraByLang.keys.map { lang ->
            LeftItem.Language(lang, langLabels[lang.lowercase()] ?: lang.uppercase())
        }
        addAll(langItems.sortedBy { it.label })
    }

    val selectedLangFromUrl = selectedExternalUrl?.let { url ->
        extraTracks.find { it.url == url }?.lang
    }
    var selectedLeft by remember {
        mutableStateOf(
            when {
                nothingSelected -> LeftItem.Off
                selectedLangFromUrl != null -> {
                    val lang = selectedLangFromUrl
                    LeftItem.Language(lang, langLabels[lang.lowercase()] ?: lang.uppercase())
                }
                else -> LeftItem.Off
            }
        )
    }
    if (selectedLeft !in leftItems) selectedLeft = leftItems.firstOrNull() ?: LeftItem.Off

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 280.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val leftScrollState = rememberScrollState()
        Box(
            modifier = Modifier
                .width(135.dp)
                .fillMaxHeight()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(leftScrollState),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
            leftItems.forEach { item ->
                val isSelected = selectedLeft == item
                val label = when (item) {
                    is LeftItem.Off -> "Off"
                    is LeftItem.Language -> item.label
                }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedLeft = item
                            if (item is LeftItem.Off) onEmbeddedSelected(-1)
                        },
                    shape = MaterialTheme.shapes.small,
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                    else Color.Transparent
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
            }
            }
            if (leftScrollState.maxValue > 0) {
                Canvas(
                    modifier = Modifier
                        .width(6.dp)
                        .fillMaxHeight()
                        .align(Alignment.CenterEnd)
                ) {
                    val viewportHeight = size.height
                    val totalContent = leftScrollState.maxValue + viewportHeight
                    val thumbHeight = (viewportHeight * viewportHeight / totalContent).coerceAtLeast(24f)
                    val scrollRange = (size.height - thumbHeight).coerceAtLeast(0f)
                    val thumbOffset = if (leftScrollState.maxValue > 0) {
                        (leftScrollState.value.toFloat() / leftScrollState.maxValue) * scrollRange
                    } else 0f
                    drawRect(
                        color = Color.Gray.copy(alpha = 0.3f),
                        topLeft = Offset(size.width - 4f, 0f),
                        size = Size(4f, size.height)
                    )
                    drawRect(
                        color = Color.Gray.copy(alpha = 0.8f),
                        topLeft = Offset(size.width - 4f, thumbOffset),
                        size = Size(4f, thumbHeight)
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            when (val item = selectedLeft) {
                is LeftItem.Off -> {
                    Text(
                        "Subtitles disabled",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                is LeftItem.Language -> {
                    val options = extraByLang[item.lang].orEmpty()
                    options.forEach { track ->
                        val url = track.url ?: return@forEach
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onExternalSelected(url) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selectedExternalUrl == url, onClick = { onExternalSelected(url) })
                            Spacer(Modifier.width(8.dp))
                            Text(track.name, color = if (selectedExternalUrl == url) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    if (options.isEmpty()) {
                        Text("No options", style = MaterialTheme.typography.bodySmall, color = Color.Gray, modifier = Modifier.padding(12.dp))
                    }
                }
            }
        }
    }

    if (extraTracks.isEmpty()) {
        Text("No subtitles available", style = MaterialTheme.typography.bodySmall, color = Color.Gray, modifier = Modifier.padding(12.dp))
    }

    if (selectedExternalUrl != null) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Text("Delay: ${delayMs / 1000f}s", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(90.dp))
            Slider(
                value = (delayMs / 5000f).coerceIn(-1f, 1f),
                onValueChange = { onDelayChange((it * 5000).toLong()) },
                valueRange = -1f..1f,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private sealed class LeftItem {
    data object Off : LeftItem()
    data class Language(val lang: String, val label: String) : LeftItem()
}

@Composable
private fun AudioSection(
    tracks: List<AudioTrack>,
    selectedId: Int,
    onTrackSelected: (Int) -> Unit,
) {
    Text("Audio Track", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
    if (tracks.isEmpty()) {
        Text("No alternate audio tracks", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
    } else {
        Column {
            tracks.forEach { track ->
                val id = track.id.toIntOrNull() ?: return@forEach
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onTrackSelected(id) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = selectedId == id, onClick = { onTrackSelected(id) })
                    Spacer(Modifier.width(8.dp))
                    Text(track.name, color = if (selectedId == id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpeedSection(
    currentRate: Float,
    onRateSelected: (Float) -> Unit,
) {
    Text("Playback Speed", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SPEED_OPTIONS.forEach { rate ->
            val label = if (rate == 1f) "1x" else "${rate}x"
            FilterChip(
                selected = abs(currentRate - rate) < 0.01f,
                onClick = { onRateSelected(rate) },
                label = { Text(label) }
            )
        }
    }
}

@Composable
private fun OptionsSection(
    onCopyLink: () -> Unit,
    onOpenExternally: () -> Unit,
) {
    Text("Options", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onCopyLink) {
            Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Copy stream link")
        }
        Spacer(Modifier.width(16.dp))
        TextButton(onClick = onOpenExternally) {
            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Open in VLC")
        }
    }
}
