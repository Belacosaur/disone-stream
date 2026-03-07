package com.disone.ui.screens

import android.view.View
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.disone.core.addons.DisoneStream
import com.disone.core.models.PlayResponse
import com.disone.core.playback.PendingPlayHolder
import com.disone.core.player.VlcPlayerEngine
import com.disone.core.streaming.StreamingRepository
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
import javax.inject.Inject

data class PlayerState(
    val isBuffering: Boolean = false,
    val error: String? = null,
    val peerCount: Int = 0,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = -1L,
    val isFullscreen: Boolean = false
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val streamingRepository: StreamingRepository,
    private val torrentSession: TorrentSession,
    private val vlcPlayer: VlcPlayerEngine,
    private val pendingPlayHolder: PendingPlayHolder
) : ViewModel() {

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var playJob: Job? = null
    private var progressJob: Job? = null

    init {
        vlcPlayer.setOnPlaybackError { message ->
            _state.value = _state.value.copy(isBuffering = false, error = message)
        }
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

    private fun startProgressPolling() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (true) {
                delay(500)
                val pos = vlcPlayer.getTime()
                val dur = vlcPlayer.getLength()
                val playing = vlcPlayer.isPlaying()
                _state.value = _state.value.copy(
                    positionMs = if (pos >= 0) pos else _state.value.positionMs,
                    durationMs = if (dur >= 0) dur else _state.value.durationMs,
                    isPlaying = playing
                )
                if (!playing && pos < 0) break // stopped
            }
        }
    }

    private fun stopProgressPolling() {
        progressJob?.cancel()
        progressJob = null
    }

    fun playFromPending() {
        val stream = pendingPlayHolder.takePending()
        if (stream != null) {
            playStream(stream)
        } else {
            _state.value = _state.value.copy(isBuffering = false, error = "No stream to play")
        }
    }

    fun playStream(stream: DisoneStream) {
        playJob?.cancel()
        playJob = viewModelScope.launch {
            _state.value = _state.value.copy(isBuffering = true, error = null)
            val result = streamingRepository.playStream(stream)
            result.fold(
                onSuccess = { response ->
                    val magnet = stream.magnet ?: stream.infoHash?.let { "magnet:?xt=urn:btih:$it" } ?: ""
                    handlePlayResponse(response, magnet, stream.fileIdx ?: 0)
                },
                onFailure = { _state.value = _state.value.copy(isBuffering = false, error = it.message) }
            )
        }
    }

    fun play(magnet: String, fileIndex: Int) {
        playJob?.cancel()
        playJob = viewModelScope.launch {
            _state.value = _state.value.copy(isBuffering = true, error = null)
            val result = streamingRepository.play(magnet, fileIndex)
            result.fold(
                onSuccess = { response -> handlePlayResponse(response, magnet, fileIndex) },
                onFailure = { _state.value = _state.value.copy(isBuffering = false, error = it.message) }
            )
        }
    }

    private suspend fun handlePlayResponse(response: PlayResponse, magnet: String, fileIndex: Int) {
        when (response.mode.uppercase()) {
            "P2P" -> {
                val infoHash = response.torrentMetadata?.infoHash
                if (infoHash != null) {
                    torrentSession.addTorrent(magnet, fileIndex)
                    waitForBuffer(infoHash, fileIndex)
                } else {
                    _state.value = _state.value.copy(isBuffering = false, error = "No torrent metadata")
                }
            }
            "HOSTED_PROGRESSIVE" -> {
                val url = response.streamUrl?.trim()
                if (!url.isNullOrBlank()) {
                    vlcPlayer.playUrl(url)
                    _state.value = _state.value.copy(isBuffering = false, isPlaying = true)
                    startProgressPolling()
                } else {
                    _state.value = _state.value.copy(isBuffering = false, error = "No stream URL")
                }
            }
            "HOSTED_HLS" -> {
                // Server-side transcoding isn't implemented yet. Use progressive stream instead -
                // VLC can play HEVC directly; no transcoding needed. Build /stream/ URL from /hls/ path.
                val hlsUrl = response.hlsUrl?.trim()
                if (!hlsUrl.isNullOrBlank()) {
                    val streamUrl = hlsUrl
                        .replace("/hls/", "/stream/")
                        .replace("/playlist.m3u8", "")
                    vlcPlayer.playUrl(streamUrl)
                    _state.value = _state.value.copy(isBuffering = false, isPlaying = true)
                    startProgressPolling()
                } else {
                    _state.value = _state.value.copy(isBuffering = false, error = "No HLS URL")
                }
            }
            else -> {
                _state.value = _state.value.copy(isBuffering = false, error = "Unknown mode: ${response.mode}")
            }
        }
    }

    private suspend fun waitForBuffer(infoHash: String, fileIndex: Int) {
        val minBufferBytes = 5 * 1024 * 1024L // 5MB
        repeat(120) {
            val progress = torrentSession.getProgress(infoHash)
            if (progress != null) {
                _state.value = _state.value.copy(peerCount = progress.numPeers)
                if (progress.progressBytes >= minBufferBytes) {
                    val path = torrentSession.getFilePath(infoHash, fileIndex)
                    if (path != null && File(path).exists()) {
                        vlcPlayer.playLocal(path)
                        _state.value = _state.value.copy(isBuffering = false, isPlaying = true)
                        startProgressPolling()
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
        playJob?.cancel()
        stopProgressPolling()
        vlcPlayer.stop()
        _state.value = _state.value.copy(isPlaying = false, positionMs = 0L, durationMs = -1L)
    }
}
