package com.disone.ui.screens

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.disone.core.addons.AddonMeta
import com.disone.core.addons.AddonParser
import com.disone.core.addons.AddonService
import com.disone.core.addons.AddonVideo
import com.disone.core.addons.DisoneStream
import com.disone.core.addons.StreamNormalizer
import com.disone.core.auth.AuthRepository
import com.disone.core.auth.AuthState
import com.disone.core.library.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val CINEMETA_URL = "https://v3-cinemeta.strem.io/"
private const val TORRENTIO_URL = "https://torrentio.strem.fun/lite/manifest.json"

data class EpisodesState(
    val meta: AddonMeta? = null,
    val seasons: List<Int> = emptyList(),
    val selectedSeason: Int = 1,
    val episodes: List<AddonVideo> = emptyList(),
    val selectedEpisode: AddonVideo? = null,
    val streams: List<StreamOptionUi> = emptyList(),
    val isMetaLoading: Boolean = false,
    val isStreamsLoading: Boolean = false,
    val showStreams: Boolean = false,
    val savedProgressMs: Long = 0L,
    /** Episode ID that savedProgressMs applies to (for resume) */
    val resumeVideoId: String? = null
)

@HiltViewModel
class EpisodesViewModel @Inject constructor(
    private val addonService: AddonService,
    private val addonParser: AddonParser,
    private val streamNormalizer: StreamNormalizer,
    private val authRepository: AuthRepository,
    private val libraryRepository: LibraryRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _state = MutableStateFlow(EpisodesState())
    val state: StateFlow<EpisodesState> = _state.asStateFlow()

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

    fun loadMeta(itemId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isMetaLoading = true, showStreams = false)
            val (type, id) = when (val idx = itemId.indexOf(':')) {
                -1 -> "series" to itemId
                else -> itemId.take(idx) to itemId.drop(idx + 1)
            }
            if (type != "series") return@launch

            val libraryItemId = "series:$id"
            val progress = libraryRepository.getProgress(libraryItemId).getOrNull()
            val savedProgressMs = progress?.timeOffsetMs ?: 0L
            val resumeVideoId = progress?.videoId

            val metaResult = addonService.fetchMeta(CINEMETA_URL, type, id).fold(
                onSuccess = { json -> addonParser.parseMetaResponse(json) },
                onFailure = { Result.failure(it) }
            )
            val meta = metaResult.getOrNull()?.meta
            if (meta == null || meta.videos.isNullOrEmpty()) {
                _state.value = _state.value.copy(
                    isMetaLoading = false,
                    meta = meta,
                    savedProgressMs = savedProgressMs
                )
                return@launch
            }

            val videos = meta.videos
            val seasonSet = videos.mapNotNull { it.season }.toSortedSet()
            val seasons = seasonSet.toList()
            val resumeEpisode = resumeVideoId?.let { vid -> videos.find { it.id == vid } }
            val (defaultSeason, episodesForSeason) = if (resumeEpisode != null) {
                val s = resumeEpisode.season ?: 1
                s to videos.filter { it.season == s }.sortedBy { it.episode ?: it.number ?: 0 }
            } else {
                val s = seasons.firstOrNull() ?: 1
                s to videos.filter { it.season == s }.sortedBy { it.episode ?: it.number ?: 0 }
            }

            _state.value = _state.value.copy(
                meta = meta,
                seasons = seasons,
                selectedSeason = defaultSeason,
                episodes = episodesForSeason,
                selectedEpisode = resumeEpisode,
                isMetaLoading = false,
                savedProgressMs = savedProgressMs,
                resumeVideoId = resumeVideoId
            )
            if (resumeEpisode != null && savedProgressMs > 0) {
                loadStreamsForEpisode(resumeEpisode)
            }
        }
    }

    fun selectSeason(season: Int) {
        val meta = _state.value.meta ?: return
        val videos = meta.videos ?: return
        val episodesForSeason = videos.filter { it.season == season }
            .sortedBy { it.episode ?: it.number ?: 0 }
        _state.value = _state.value.copy(
            selectedSeason = season,
            episodes = episodesForSeason,
            selectedEpisode = null,
            showStreams = false
        )
    }

    fun selectEpisode(episode: AddonVideo) {
        _state.value = _state.value.copy(selectedEpisode = episode)
    }

    fun loadStreamsForEpisode(episode: AddonVideo) {
        val meta = _state.value.meta ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isStreamsLoading = true,
                selectedEpisode = episode,
                showStreams = true
            )
            val plan = (authRepository.authState.first() as? AuthState.Authenticated)?.plan?.uppercase() ?: "P2P"

            var disoneStreams = fetchStreamsFromTorrentio("series", episode.id)
            if (disoneStreams.isEmpty()) {
                delay(200)
                disoneStreams = fetchStreamsFromTorrentio("series", episode.id)
            }
            val withContext = disoneStreams.map {
                it.copy(videoType = "series", videoId = episode.id)
            }

            val streams = withContext.map { s ->
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

            _state.value = _state.value.copy(
                streams = streams,
                isStreamsLoading = false
            )
        }
    }

    fun backToEpisodes() {
        _state.value = _state.value.copy(
            showStreams = false,
            streams = emptyList()
        )
    }
}
