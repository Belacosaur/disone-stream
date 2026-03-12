package com.disone.ui.screens

import android.view.View
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.disone.core.addons.AddonManager
import com.disone.core.addons.AddonService
import com.disone.core.addons.DisoneStream
import com.disone.core.subscription.PurchaseConfirmation
import com.disone.core.subscription.SubscriptionUseCase
import com.disone.core.subscription.VerificationPendingException
import com.disone.core.subtitle.SubtitleCue
import com.disone.core.subtitle.SubtitleParser
import com.disone.core.models.PlayResponse
import com.disone.core.models.SubscriptionPackage
import com.disone.core.player.AudioTrack
import com.disone.core.player.SubtitleTrack
import com.disone.core.auth.AuthRepository
import com.disone.core.access.AccessRepository
import com.disone.core.library.LibraryRepository
import com.disone.core.playback.PendingPlayHolder
import com.disone.core.player.VlcPlayerEngine
import com.disone.core.streaming.StreamingRepository
import com.disone.core.utils.toItemId
import com.disone.core.addons.DefaultAddons
import com.disone.core.torrent.TorrentSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.videolan.libvlc.util.VLCVideoLayout
import java.io.File
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import javax.inject.Inject

data class PlayerState(
    val accessDenied: Boolean = false,
    val accessDeniedReason: String? = null, // "expired" | "subscription_required"
    val accessChecking: Boolean = false,
    val isBuffering: Boolean = false,
    /** Title/background/logo shown while buffering (Stremio-style loading overlay) */
    val loadingTitle: String? = null,
    val loadingBackgroundUrl: String? = null,
    val loadingLogoUrl: String? = null,
    val error: String? = null,
    val peerCount: Int = 0,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = -1L,
    val isFullscreen: Boolean = false,
    // Stremio-style options
    val playbackRate: Float = 1f,
    val volume: Int = 80,
    val isMuted: Boolean = false,
    val subtitleTracks: List<SubtitleTrack> = emptyList(),
    val selectedSubtitleTrackId: Int = -1,
    val extraSubtitleTracks: List<SubtitleTrack> = emptyList(),
    val selectedExternalSubtitleUrl: String? = null,
    /** Parsed cues for custom overlay (avoids libVLC restart) */
    val overlaySubtitleCues: List<SubtitleCue> = emptyList(),
    val subtitleDelayMs: Long = 0L,
    val audioTracks: List<AudioTrack> = emptyList(),
    val selectedAudioTrackId: Int = -1,
    val currentStreamUrl: String? = null,
    val currentMediaPath: String? = null,
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val streamingRepository: StreamingRepository,
    private val torrentSession: TorrentSession,
    private val vlcPlayer: VlcPlayerEngine,
    private val pendingPlayHolder: PendingPlayHolder,
    private val addonManager: AddonManager,
    private val addonService: AddonService,
    private val libraryRepository: LibraryRepository,
    private val accessRepository: AccessRepository,
    private val authRepository: AuthRepository,
    private val subscriptionUseCase: SubscriptionUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _packages = MutableStateFlow<List<SubscriptionPackage>>(emptyList())
    val packages: StateFlow<List<SubscriptionPackage>> = _packages.asStateFlow()
    private val _recipientAddress = MutableStateFlow<String?>(null)
    val recipientAddress: StateFlow<String?> = _recipientAddress.asStateFlow()
    private val _packagesLoading = MutableStateFlow(false)
    val packagesLoading: StateFlow<Boolean> = _packagesLoading.asStateFlow()
    private val _purchaseInProgress = MutableStateFlow(false)
    val purchaseInProgress: StateFlow<Boolean> = _purchaseInProgress.asStateFlow()
    /** Package we're currently fetching tx for (phase 1). */
    private val _preparingPackage = MutableStateFlow<SubscriptionPackage?>(null)
    val preparingPackage = _preparingPackage.asStateFlow()

    /** When non-null, tx is fetched and ready – tap "Confirm Payment" to open wallet. */
    private val _preparedForPurchase = MutableStateFlow<Pair<SubscriptionPackage, com.disone.core.subscription.SubscriptionRepository.CreateTransactionResult>?>(null)
    val preparedForPurchase = _preparedForPurchase.asStateFlow()
    private val _pendingVerification = MutableStateFlow<Pair<String, String>?>(null)
    val pendingVerification: StateFlow<Pair<String, String>?> = _pendingVerification.asStateFlow()
    private val _purchaseConfirmation = MutableStateFlow<PurchaseConfirmation?>(null)
    val purchaseConfirmation: StateFlow<PurchaseConfirmation?> = _purchaseConfirmation.asStateFlow()

    private var playJob: Job? = null
    private var progressJob: Job? = null
    private var lastProgressUpdateMs: Long = 0
    private var currentLibraryItemId: String? = null
    /** For series: episode ID (tt0944947:1:1) for progress. Null for movies. */
    private var currentVideoId: String? = null
    private var pendingMagnetAndIndex: Pair<String, Int>? = null

    init {
        vlcPlayer.setOnPlaybackError { message ->
            _state.value = _state.value.copy(isBuffering = false, error = message)
        }
        vlcPlayer.setOnMediaReady { refreshTracksAndOptions() }
    }

    private fun refreshTracksAndOptions() {
        val subs = vlcPlayer.getSubtitleTracks()
        val audios = vlcPlayer.getAudioTracks()
        val rate = vlcPlayer.getRate()
        val vol = vlcPlayer.getVolume()
        val muted = vlcPlayer.isMuted()
        val subId = vlcPlayer.getSubtitleTrack()
        val audioId = vlcPlayer.getAudioTrack()
        val subDelay = vlcPlayer.getSubtitleDelay()
        _state.value = _state.value.copy(
            subtitleTracks = subs,
            selectedSubtitleTrackId = subId,
            audioTracks = audios,
            selectedAudioTrackId = audioId,
            playbackRate = rate,
            volume = vol,
            isMuted = muted,
            subtitleDelayMs = subDelay,
        )
    }

    fun togglePlayPause() {
        if (vlcPlayer.isPlaying()) {
            vlcPlayer.pause()
            _state.value = _state.value.copy(isPlaying = false)
        } else {
            vlcPlayer.play()
            _state.value = _state.value.copy(isPlaying = true)
        }
    }

    fun seekTo(positionMs: Long) {
        vlcPlayer.seekTo(positionMs)
        _state.value = _state.value.copy(positionMs = positionMs)
    }

    fun setFullscreen(fullscreen: Boolean) {
        _state.value = _state.value.copy(isFullscreen = fullscreen)
    }

    fun setPlaybackRate(rate: Float) {
        vlcPlayer.setRate(rate)
        _state.value = _state.value.copy(playbackRate = rate)
    }

    fun setVolume(volume: Int) {
        vlcPlayer.setVolume(volume)
        _state.value = _state.value.copy(volume = volume, isMuted = volume == 0)
    }

    fun setMuted(muted: Boolean) {
        vlcPlayer.setMuted(muted)
        _state.value = _state.value.copy(isMuted = muted)
    }

    fun toggleMute() {
        val muted = !_state.value.isMuted
        setMuted(muted)
    }

    fun setSubtitleTrack(trackId: Int) {
        vlcPlayer.setSubtitleTrack(trackId)
        _state.value = _state.value.copy(
            selectedSubtitleTrackId = trackId,
            selectedExternalSubtitleUrl = null,
            overlaySubtitleCues = emptyList()
        )
    }

    fun loadExternalSubtitle(url: String) {
        viewModelScope.launch {
            val subFile = if (url.startsWith("http://") || url.startsWith("https://")) {
                addonService.downloadSubtitleToFile(url).getOrNull()
            } else {
                val path = url.removePrefix("file://")
                File(path).takeIf { it.exists() }
            } ?: return@launch

            vlcPlayer.setSubtitleTrack(-1)
            val cues = runCatching { SubtitleParser.parse(subFile) }.getOrElse { emptyList() }
            _state.value = _state.value.copy(
                selectedSubtitleTrackId = -1,
                selectedExternalSubtitleUrl = url,
                overlaySubtitleCues = cues
            )
        }
    }

    fun setSubtitleDelay(delayMs: Long) {
        vlcPlayer.setSubtitleDelay(delayMs)
        _state.value = _state.value.copy(subtitleDelayMs = delayMs)
    }

    fun setAudioTrack(trackId: Int) {
        vlcPlayer.setAudioTrack(trackId)
        _state.value = _state.value.copy(selectedAudioTrackId = trackId)
    }

    fun refreshPlayerOptions() {
        refreshTracksAndOptions()
    }

    fun getStreamUrlForSharing(): String? = _state.value.currentStreamUrl

    private fun startProgressPolling() {
        progressJob?.cancel()
        lastProgressUpdateMs = 0
        var pollCount = 0
        progressJob = viewModelScope.launch {
            while (true) {
                delay(500)
                pollCount++
                val pos = vlcPlayer.getTime()
                val dur = vlcPlayer.getLength()
                val playing = vlcPlayer.isPlaying()
                _state.value = _state.value.copy(
                    positionMs = if (pos >= 0) pos else _state.value.positionMs,
                    durationMs = if (dur >= 0) dur else _state.value.durationMs,
                    isPlaying = playing
                )
                if (!playing && pos < 0) break // stopped
                if (pollCount % 6 == 0) refreshTracksAndOptions()
                if (pollCount % 60 == 0) syncProgressToLibrary(pos, dur)
            }
        }
    }

    private fun syncProgressToLibrary(positionMs: Long, durationMs: Long) {
        val libId = currentLibraryItemId ?: return
        if (positionMs < 0 || durationMs <= 0) return
        val now = System.currentTimeMillis()
        if (now - lastProgressUpdateMs < 30_000) return
        lastProgressUpdateMs = now
        viewModelScope.launch {
            libraryRepository.updateProgress(libId, positionMs, durationMs, currentVideoId).onSuccess {
                lastProgressUpdateMs = now
            }
        }
    }

    private fun stopProgressPolling() {
        progressJob?.cancel()
        progressJob = null
    }

    fun playFromPending() {
        playJob?.cancel()
        val stream = pendingPlayHolder.peekPending()
        if (stream == null) {
            _state.value = _state.value.copy(accessChecking = false, accessDenied = false, error = "No stream to play")
            return
        }
        playJob = viewModelScope.launch {
            _state.value = _state.value.copy(accessChecking = true, accessDenied = false, accessDeniedReason = null)
            val access = accessRepository.checkAccess()
            _state.value = _state.value.copy(accessChecking = false)
            access.fold(
                onSuccess = { resp ->
                    if (resp.canWatch) {
                        val (s, startPositionMs) = pendingPlayHolder.takePending()
                        if (s != null) {
                            playStream(s, startPositionMs)
                        } else {
                            _state.value = _state.value.copy(error = "No stream to play")
                        }
                    } else {
                        _state.value = _state.value.copy(accessDenied = true, accessDeniedReason = resp.reason, error = null)
                    }
                },
                onFailure = {
                    _state.value = _state.value.copy(accessDenied = false, error = it.message ?: "Failed to verify access")
                }
            )
        }
    }

    fun play(magnet: String, fileIndex: Int) {
        playJob?.cancel()
        pendingMagnetAndIndex = magnet to fileIndex
        playJob = viewModelScope.launch {
            _state.value = _state.value.copy(accessChecking = true, accessDenied = false, accessDeniedReason = null)
            val access = accessRepository.checkAccess()
            _state.value = _state.value.copy(accessChecking = false)
            access.fold(
                onSuccess = { resp ->
                    if (resp.canWatch) {
                        doPlay(magnet, fileIndex)
                    } else {
                        _state.value = _state.value.copy(accessDenied = true, accessDeniedReason = resp.reason, error = null)
                    }
                },
                onFailure = {
                    _state.value = _state.value.copy(accessDenied = false, error = it.message ?: "Failed to verify access")
                }
            )
        }
    }

    fun loadPackages() {
        viewModelScope.launch {
            _packagesLoading.value = true
            subscriptionUseCase.getPackages()
                .onSuccess { result ->
                    _packages.value = result.packages
                    _recipientAddress.value = result.recipientAddress
                }
                .onFailure {
                    _packages.value = emptyList()
                    _recipientAddress.value = null
                }
            _packagesLoading.value = false
        }
    }

    /** Step 1: Fetch tx from backend. No Phantom. Call when user taps a package. */
    fun prepareForPurchase(packageItem: SubscriptionPackage, onResult: (Result<Unit>) -> Unit) {
        val wallet = authRepository.getCurrentWallet()
        if (wallet == null) {
            onResult(Result.failure(Exception("Not logged in. Sign in first.")))
            return
        }
        viewModelScope.launch {
            _purchaseInProgress.value = true
            _preparingPackage.value = packageItem
            _preparedForPurchase.value = null
            val txResult = subscriptionUseCase.prepareTransaction(packageItem.packageKey, wallet)
            _preparingPackage.value = null
            _purchaseInProgress.value = false
            txResult.fold(
                onSuccess = { tx -> _preparedForPurchase.value = packageItem to tx; onResult(Result.success(Unit)) },
                onFailure = { onResult(Result.failure(it)) }
            )
        }
    }

    /** Step 2: Open Phantom with PRE-FETCHED tx. Only call after prepareForPurchase succeeds. */
    fun confirmPurchase(
        activityResultSender: ActivityResultSender,
        onResult: (Result<PurchaseConfirmation>, canRetryVerification: Boolean) -> Unit
    ) {
        val prepared = _preparedForPurchase.value ?: run {
            onResult(Result.failure(Exception("Transaction expired. Tap a plan again.")), false)
            return
        }
        val (packageItem, txResult) = prepared
        viewModelScope.launch {
            _purchaseInProgress.value = true
            _preparedForPurchase.value = null
            _purchaseConfirmation.value = null
            val result = subscriptionUseCase.signAndCompletePurchase(activityResultSender, packageItem, txResult)
            _purchaseInProgress.value = false
            result.onSuccess { confirmation ->
                _pendingVerification.value = null
                _purchaseConfirmation.value = confirmation
                onResult(result, false)
            }.onFailure { e ->
                val canRetry = e is VerificationPendingException
                if (canRetry) {
                    _pendingVerification.value = (e as VerificationPendingException).txSignature to e.packageKey
                } else {
                    _pendingVerification.value = null
                }
                onResult(result, canRetry)
            }
        }
    }

    fun clearPreparedForPurchase() {
        _preparingPackage.value = null
        _preparedForPurchase.value = null
    }

    /** Legacy one-tap (kept for fallback). Prefer prepareForPurchase + confirmPurchase. */
    fun purchasePackage(
        activityResultSender: ActivityResultSender,
        packageItem: SubscriptionPackage,
        onResult: (Result<PurchaseConfirmation>, canRetryVerification: Boolean) -> Unit
    ) {
        prepareForPurchase(packageItem) { prepareResult ->
            prepareResult.onSuccess {
                confirmPurchase(activityResultSender, onResult)
            }.onFailure { onResult(Result.failure(it), false) }
        }
    }

    fun retryPendingVerification(onResult: (Result<PurchaseConfirmation>) -> Unit) {
        val pending = _pendingVerification.value ?: return
        viewModelScope.launch {
            _purchaseInProgress.value = true
            val result = subscriptionUseCase.confirmBySignature(pending.first, pending.second)
            _purchaseInProgress.value = false
            result.onSuccess { confirmation ->
                _pendingVerification.value = null
                _purchaseConfirmation.value = confirmation
            }
            onResult(result)
        }
    }

    fun clearPendingVerification() {
        _pendingVerification.value = null
    }

    fun dismissPurchaseConfirmation() {
        _purchaseConfirmation.value = null
    }

    /** Called after in-player purchase — retry play if access now granted. */
    fun retryAfterPurchase() {
        playJob?.cancel()
        playJob = viewModelScope.launch {
            _state.value = _state.value.copy(accessChecking = true, accessDenied = false, accessDeniedReason = null)
            val access = accessRepository.checkAccess()
            _state.value = _state.value.copy(accessChecking = false)
            access.fold(
                onSuccess = { resp ->
                    if (resp.canWatch) {
                        val (stream, startPositionMs) = pendingPlayHolder.takePending()
                        if (stream != null) {
                            playStream(stream, startPositionMs)
                        } else {
                            val args = pendingMagnetAndIndex
                            if (args != null) {
                                pendingMagnetAndIndex = null
                                doPlay(args.first, args.second)
                            } else {
                                _state.value = _state.value.copy(error = "No stream to play")
                            }
                        }
                    } else {
                        _state.value = _state.value.copy(accessDenied = true, accessDeniedReason = resp.reason)
                    }
                },
                onFailure = {
                    _state.value = _state.value.copy(accessDenied = true, accessDeniedReason = null, error = it.message)
                }
            )
        }
    }

    private fun doPlay(magnet: String, fileIndex: Int) {
        playJob?.cancel()
        currentLibraryItemId = null
        currentVideoId = null
        _state.value = _state.value.copy(extraSubtitleTracks = emptyList(), selectedExternalSubtitleUrl = null, overlaySubtitleCues = emptyList(), loadingTitle = null, loadingBackgroundUrl = null, loadingLogoUrl = null)
        playJob = viewModelScope.launch {
            _state.value = _state.value.copy(isBuffering = true, error = null)
            val result = streamingRepository.play(magnet, fileIndex)
            result.fold(
                onSuccess = { response -> handlePlayResponse(response, magnet, fileIndex) },
                onFailure = { _state.value = _state.value.copy(isBuffering = false, error = it.message) }
            )
        }
    }

    fun playStream(stream: DisoneStream, startPositionMs: Long = 0L) {
        playJob?.cancel()
        val videoType = stream.videoType ?: "movie"
        val videoId = stream.videoId?.trim()
        // For series: library item is the series (series:tt0944947), videoId is the episode (tt0944947:1:1)
        if (videoType == "series" && videoId != null && ":" in videoId) {
            val seriesId = videoId.substringBefore(":")
            currentLibraryItemId = toItemId("series", seriesId)
            currentVideoId = videoId
        } else {
            currentLibraryItemId = stream.videoType?.let { t -> stream.videoId?.let { id -> toItemId(t, id) } }
            currentVideoId = null
        }
        _state.value = _state.value.copy(extraSubtitleTracks = emptyList(), selectedExternalSubtitleUrl = null, overlaySubtitleCues = emptyList())
        // Pre-fill loading overlay; fetch meta for cover/background/logo; auto-add series to library for progress
        val metaId = when {
            videoId == null -> null
            videoType == "series" && ":" in videoId -> videoId.substringBefore(":")
            else -> videoId
        }
        val canFetchMeta = !metaId.isNullOrBlank() && (metaId.startsWith("tt") || metaId.startsWith("kx"))
        _state.value = _state.value.copy(
            isBuffering = true,
            error = null,
            loadingTitle = stream.title.takeIf { it.isNotBlank() },
            loadingBackgroundUrl = null,
            loadingLogoUrl = null
        )
        if (canFetchMeta && metaId != null) {
            viewModelScope.launch {
                addonManager.getMeta(DefaultAddons.cinemetaUrl, videoType, metaId).getOrNull()?.let { meta ->
                    _state.value = _state.value.copy(
                        loadingTitle = meta.name.takeIf { it.isNotBlank() } ?: _state.value.loadingTitle,
                        loadingBackgroundUrl = meta.background ?: meta.poster,
                        loadingLogoUrl = meta.logo
                    )
                    // Ensure series is in library so progress can be saved (updateProgress requires item to exist)
                    if (videoType == "series") {
                        libraryRepository.add(
                            toItemId("series", metaId),
                            meta.name,
                            "series",
                            metaId,
                            meta.poster
                        )
                    }
                }
            }
        }
        playJob = viewModelScope.launch {
            val result = streamingRepository.playStream(stream)
            result.fold(
                onSuccess = { response ->
                    val magnet = stream.magnet ?: stream.infoHash?.let { "magnet:?xt=urn:btih:$it" } ?: ""
                    handlePlayResponse(response, magnet, stream.fileIdx ?: 0, startPositionMs)
                    fetchAddonSubtitlesIfNeeded(stream.videoType, stream.videoId)
                },
                onFailure = { _state.value = _state.value.copy(isBuffering = false, error = it.message) }
            )
        }
    }

    /**
     * Registers VLC's onMediaReady so the cover/logo overlay stays until the first frame plays.
     * Clears buffering and loading overlay when VLC fires Playing event.
     */
    private fun setupMediaReadyCallback(streamUrl: String? = null, mediaPath: String? = null, startPositionMs: Long = 0L) {
        vlcPlayer.setOnMediaReady {
            _state.value = _state.value.copy(
                isBuffering = false,
                isPlaying = true,
                currentStreamUrl = streamUrl,
                currentMediaPath = mediaPath,
                loadingTitle = null,
                loadingBackgroundUrl = null,
                loadingLogoUrl = null
            )
            startProgressPolling()
            if (startPositionMs > 0) {
                viewModelScope.launch {
                    delay(500)
                    vlcPlayer.seekTo(startPositionMs)
                    _state.value = _state.value.copy(positionMs = startPositionMs)
                }
            }
        }
    }

    private suspend fun handlePlayResponse(response: PlayResponse, magnet: String, fileIndex: Int, startPositionMs: Long = 0L) {
        when (response.mode.uppercase()) {
            "P2P" -> {
                val infoHash = response.torrentMetadata?.infoHash
                if (infoHash != null) {
                    torrentSession.addTorrent(magnet, fileIndex)
                    waitForBuffer(infoHash, fileIndex, startPositionMs)
                } else {
                    _state.value = _state.value.copy(isBuffering = false, error = "No torrent metadata")
                }
            }
            "HOSTED_PROGRESSIVE" -> {
                val url = response.streamUrl?.trim()
                if (!url.isNullOrBlank()) {
                    setupMediaReadyCallback(streamUrl = url, startPositionMs = startPositionMs)
                    vlcPlayer.playUrl(url)
                } else {
                    _state.value = _state.value.copy(isBuffering = false, error = "No stream URL")
                }
            }
            "HOSTED_HLS" -> {
                val hlsUrl = response.hlsUrl?.trim()
                if (!hlsUrl.isNullOrBlank()) {
                    val streamUrl = hlsUrl
                        .replace("/hls/", "/stream/")
                        .replace("/playlist.m3u8", "")
                    setupMediaReadyCallback(streamUrl = streamUrl, startPositionMs = startPositionMs)
                    vlcPlayer.playUrl(streamUrl)
                } else {
                    _state.value = _state.value.copy(isBuffering = false, error = "No HLS URL")
                }
            }
            else -> {
                _state.value = _state.value.copy(isBuffering = false, error = "Unknown mode: ${response.mode}")
            }
        }
    }

    private suspend fun waitForBuffer(infoHash: String, fileIndex: Int, startPositionMs: Long = 0L) {
        val minBufferBytes = 5 * 1024 * 1024L // 5MB
        repeat(120) {
            val progress = torrentSession.getProgress(infoHash)
            if (progress != null) {
                _state.value = _state.value.copy(peerCount = progress.numPeers)
                if (progress.progressBytes >= minBufferBytes) {
                    val path = torrentSession.getFilePath(infoHash, fileIndex)
                    if (path != null && File(path).exists()) {
                        setupMediaReadyCallback(mediaPath = path, startPositionMs = startPositionMs)
                        vlcPlayer.playLocal(path)
                        return
                    }
                }
            }
            delay(500)
        }
        _state.value = _state.value.copy(isBuffering = false, error = "Buffering timeout")
    }

    fun attachVideoView(layout: VLCVideoLayout) {
        vlcPlayer.setVideoLayout(layout)
    }

    fun stop() {
        val pos = vlcPlayer.getTime()
        val dur = vlcPlayer.getLength()
        if (currentLibraryItemId != null && pos >= 0 && dur > 0) {
            viewModelScope.launch {
                libraryRepository.updateProgress(currentLibraryItemId!!, pos, dur, currentVideoId)
            }
        }
        currentLibraryItemId = null
        currentVideoId = null
        playJob?.cancel()
        stopProgressPolling()
        vlcPlayer.stop()
        _state.value = _state.value.copy(
            isPlaying = false,
            positionMs = 0L,
            durationMs = -1L,
            extraSubtitleTracks = emptyList(),
            selectedExternalSubtitleUrl = null,
            overlaySubtitleCues = emptyList(),
            loadingTitle = null,
            loadingBackgroundUrl = null,
            loadingLogoUrl = null
        )
    }

    private fun fetchAddonSubtitlesIfNeeded(videoType: String?, videoId: String?) {
        if (videoType.isNullOrBlank() || videoId.isNullOrBlank()) return
        viewModelScope.launch {
            addonManager.getSubtitles(videoType, videoId).fold(
                onSuccess = { rawList ->
                    val tracks = rawList.mapNotNull { raw ->
                        raw.url?.let { url ->
                            val lang = raw.lang ?: "und"
                            val langLabel = when (lang.lowercase()) {
                                "eng" -> "English"
                                "spa" -> "Spanish"
                                "fre", "fra" -> "French"
                                "ger", "deu" -> "German"
                                "por", "pob" -> "Portuguese"
                                "ita" -> "Italian"
                                "rus" -> "Russian"
                                "jpn" -> "Japanese"
                                "kor" -> "Korean"
                                "chi", "zho" -> "Chinese"
                                "ara" -> "Arabic"
                                "hin", "hi" -> "Hindi"
                                "tur" -> "Turkish"
                                "pol" -> "Polish"
                                "ukr" -> "Ukrainian"
                                "nld" -> "Dutch"
                                "swe" -> "Swedish"
                                "dan" -> "Danish"
                                "nor" -> "Norwegian"
                                "fin" -> "Finnish"
                                "ell", "gre" -> "Greek"
                                "hun" -> "Hungarian"
                                "ron" -> "Romanian"
                                "ces" -> "Czech"
                                "bul" -> "Bulgarian"
                                "hrv" -> "Croatian"
                                "srp" -> "Serbian"
                                "slk" -> "Slovak"
                                "slv" -> "Slovenian"
                                "vie" -> "Vietnamese"
                                "tha" -> "Thai"
                                "und" -> "Unknown"
                                else -> lang.uppercase()
                            }
                            val displayName = raw.name?.takeIf { it.isNotBlank() } ?: langLabel
                            SubtitleTrack(
                                id = "ext_${raw.id ?: url.hashCode()}",
                                name = displayName,
                                lang = lang,
                                isEmbedded = false,
                                url = url
                            )
                        }
                    }
                    _state.value = _state.value.copy(extraSubtitleTracks = tracks)
                },
                onFailure = { }
            )
        }
    }
}
