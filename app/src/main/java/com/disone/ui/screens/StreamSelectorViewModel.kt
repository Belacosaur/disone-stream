package com.disone.ui.screens

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.disone.core.addons.AddonParser
import com.disone.core.addons.AddonService
import com.disone.core.addons.DisoneStream
import com.disone.core.addons.StreamNormalizer
import com.disone.core.auth.AuthRepository
import com.disone.core.auth.AuthState
import com.disone.core.models.StreamOption
import com.disone.core.streaming.StreamingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TORRENTIO_URL = "https://torrentio.strem.fun/lite/manifest.json"

data class StreamOptionUi(
    val magnet: String,
    val resolution: String,
    val sizeBytes: Long,
    val seeders: Int,
    val mode: String,
    val fileIndex: Int,
    val disoneStream: DisoneStream?,
    val requiresPremium: Boolean = false
)

data class StreamSelectorState(
    val streams: List<StreamOptionUi> = emptyList(),
    val isLoading: Boolean = false,
    val magnetDialogOpen: Boolean = false
)

@HiltViewModel
class StreamSelectorViewModel @Inject constructor(
    private val streamingRepository: StreamingRepository,
    private val addonService: AddonService,
    private val addonParser: AddonParser,
    private val streamNormalizer: StreamNormalizer,
    private val authRepository: AuthRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _state = MutableStateFlow(StreamSelectorState())
    val state: StateFlow<StreamSelectorState> = _state.asStateFlow()

    /**
     * Fetches streams directly from Torrentio. No addon storage - works in dev mode.
     * Sorted by seeders (highest first), then by size (smallest first).
     */
    private suspend fun fetchStreamsFromTorrentio(type: String, videoId: String): List<DisoneStream> {
        val result = addonService.fetchStreams(TORRENTIO_URL, type, videoId).fold(
            onSuccess = { json -> addonParser.parseStreamResponse(json) },
            onFailure = { Result.failure(it) }
        )
        val streams = result.getOrNull()?.streams?.let { raw ->
            streamNormalizer.normalizeAll(raw, TORRENTIO_URL)
        }.orEmpty()
        return streams.sortedWith(
            compareBy<DisoneStream> { -(it.seeders ?: 0) }
                .thenBy { it.sizeBytes ?: Long.MAX_VALUE }
        )
    }

    fun loadStreams(itemId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            val plan = (authRepository.authState.first() as? AuthState.Authenticated)?.plan?.uppercase() ?: "P2P"

            fun mapToUi(disoneStreams: List<DisoneStream>) = disoneStreams.map { s ->
                val isHostedOnly = s.magnet == null && s.infoHash == null
                StreamOptionUi(
                    magnet = s.magnet ?: "",
                    resolution = s.quality ?: s.title,
                    sizeBytes = s.sizeBytes ?: 0L,
                    seeders = s.seeders ?: 0,
                    mode = if (s.magnet != null || s.infoHash != null) "P2P" else "HOSTED",
                    fileIndex = s.fileIdx ?: 0,
                    disoneStream = s,
                    requiresPremium = plan == "P2P" && isHostedOnly
                )
            }

            val colonIdx = itemId.indexOf(':')
            if (colonIdx > 0) {
                val type = itemId.take(colonIdx)
                val videoId = itemId.drop(colonIdx + 1)
                var disoneStreams = fetchStreamsFromTorrentio(type, videoId)
                if (disoneStreams.isEmpty()) {
                    delay(200)
                    disoneStreams = fetchStreamsFromTorrentio(type, videoId)
                }
                _state.value = _state.value.copy(streams = mapToUi(disoneStreams), isLoading = false)
            } else {
                val typesToTry = if (itemId.startsWith("tt") || itemId.startsWith("kitsu")) {
                    listOf("movie", "series", "anime")
                } else {
                    emptyList()
                }
                var addonStreams: List<DisoneStream>? = null
                for (t in typesToTry) {
                    addonStreams = fetchStreamsFromTorrentio(t, itemId)
                    if (addonStreams.isNotEmpty()) break
                }
                if (addonStreams != null && addonStreams.isNotEmpty()) {
                    _state.value = _state.value.copy(streams = mapToUi(addonStreams), isLoading = false)
                } else {
                    streamingRepository.getCatalog(page = 1).fold(
                        onSuccess = { page ->
                            val item = page.items.find { it.id == itemId }
                            val streams = item?.streams?.map { opt ->
                                StreamOptionUi(
                                    magnet = opt.magnet,
                                    resolution = opt.resolution ?: "Unknown",
                                    sizeBytes = opt.size,
                                    seeders = opt.seeders,
                                    mode = opt.mode,
                                    fileIndex = 0,
                                    disoneStream = DisoneStream(
                                        title = opt.resolution ?: "Stream",
                                        magnet = opt.magnet,
                                        fileIdx = 0
                                    )
                                )
                            } ?: emptyList()
                            _state.value = _state.value.copy(streams = streams, isLoading = false)
                        },
                        onFailure = {
                            _state.value = _state.value.copy(streams = emptyList(), isLoading = false)
                        }
                    )
                }
            }
        }
    }

    fun setManualMagnet(magnet: String) {
        if (magnet.isNotBlank()) {
            _state.value = _state.value.copy(
                streams = listOf(
                    StreamOptionUi(
                        magnet = magnet,
                        resolution = "Direct",
                        sizeBytes = 0L,
                        seeders = 0,
                        mode = "P2P",
                        fileIndex = 0,
                        disoneStream = DisoneStream(title = "Direct", magnet = magnet, fileIdx = 0),
                        requiresPremium = false
                    )
                )
            )
        }
    }

    fun showMagnetDialog() {
        _state.value = _state.value.copy(magnetDialogOpen = true)
    }

    fun dismissMagnetDialog() {
        _state.value = _state.value.copy(magnetDialogOpen = false)
    }
}
