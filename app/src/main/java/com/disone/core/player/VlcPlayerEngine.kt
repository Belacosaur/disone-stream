package com.disone.core.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import dagger.hilt.android.qualifiers.ApplicationContext
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VlcPlayerEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : PlayerEngine {

    private var libVlc: LibVLC? = null
    private var mediaPlayer: MediaPlayer? = null
    private var pendingVideoLayout: VLCVideoLayout? = null
    private var lastAttachedLayout: VLCVideoLayout? = null
    private var onPlaybackError: ((String) -> Unit)? = null
    private var onMediaReady: (() -> Unit)? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var savedVolumeBeforeMute = 80

    private fun ensureLibVlc(): LibVLC {
        if (libVlc == null) {
            val options = ArrayList<String>().apply {
                add("--aout=opensles")
                add("--http-reconnect")
                add("--network-caching=1500")
                add("--file-caching=1500")
                add("--live-caching=1500")
            }
            libVlc = LibVLC(context, options)
        }
        return libVlc!!
    }

    private fun attachLayoutIfNeeded() {
        pendingVideoLayout?.let { layout ->
            mediaPlayer?.attachViews(layout, null, true, true)
            lastAttachedLayout = layout
            pendingVideoLayout = null
        }
    }

    fun setOnPlaybackError(callback: ((String) -> Unit)?) {
        onPlaybackError = callback
    }

    fun setOnMediaReady(callback: (() -> Unit)?) {
        onMediaReady = callback
    }

    private fun createPlayerWithErrorListener(vlc: LibVLC): MediaPlayer {
        return MediaPlayer(vlc).apply {
            setEventListener(object : MediaPlayer.EventListener {
                override fun onEvent(event: MediaPlayer.Event) {
                    when (event.type) {
                        MediaPlayer.Event.EncounteredError -> {
                            val cb = onPlaybackError
                            if (cb != null) {
                                mainHandler.post { cb("Stream unavailable or playback failed") }
                            }
                        }
                        MediaPlayer.Event.Playing -> {
                            onMediaReady?.let { mainHandler.post(it) }
                        }
                        else -> {}
                    }
                }
            })
        }
    }

    override fun playLocal(path: String) {
        stop()
        val vlc = ensureLibVlc()
        mediaPlayer = createPlayerWithErrorListener(vlc).apply {
            val media = Media(vlc, path)
            media.addOption(":no-video-title-show")
            setMedia(media)
            media.release()
            play()
        }
        attachLayoutIfNeeded()
    }

    override fun playUrl(url: String) {
        stop()
        val vlc = ensureLibVlc()
        mediaPlayer = createPlayerWithErrorListener(vlc).apply {
            val media = Media(vlc, Uri.parse(url))
            media.addOption(":no-video-title-show")
            setMedia(media)
            media.release()
            play()
        }
        attachLayoutIfNeeded()
    }

    override fun stop() {
        onPlaybackError = null
        lastAttachedLayout = null
        mediaPlayer?.apply {
            stop()
            release()
        }
        mediaPlayer = null
    }

    override fun release() {
        stop()
        libVlc?.release()
        libVlc = null
    }

    override fun pause() {
        mediaPlayer?.pause()
    }

    override fun play() {
        mediaPlayer?.play()
    }

    override fun isPlaying(): Boolean = mediaPlayer?.isPlaying == true

    override fun getTime(): Long {
        val t = mediaPlayer?.time ?: return -1L
        return if (t >= 0) t else -1L
    }

    override fun getLength(): Long {
        val len = mediaPlayer?.length ?: return -1L
        return if (len > 0) len else -1L
    }

    override fun seekTo(timeMs: Long) {
        mediaPlayer?.setTime(timeMs)
    }

    fun setVideoLayout(layout: VLCVideoLayout) {
        mediaPlayer?.let { mp ->
            if (layout == lastAttachedLayout) return
            lastAttachedLayout = layout
            mp.detachViews()
            mp.attachViews(layout, null, true, true)
        } ?: run {
            pendingVideoLayout = layout
        }
    }

    fun detachViews() {
        mediaPlayer?.detachViews()
    }

    // --- Stremio-style controls ---

    override fun setRate(rate: Float) {
        mediaPlayer?.setRate(rate.coerceIn(0.25f, 4f))
    }

    override fun getRate(): Float = mediaPlayer?.getRate() ?: 1f

    override fun setVolume(volume: Int) {
        mediaPlayer?.setVolume(volume.coerceIn(0, 100))
        if (volume > 0) savedVolumeBeforeMute = volume
    }

    override fun getVolume(): Int = mediaPlayer?.getVolume() ?: 100

    override fun setMuted(muted: Boolean) {
        mediaPlayer?.let { mp ->
            if (muted) {
                savedVolumeBeforeMute = mp.getVolume().takeIf { it > 0 } ?: savedVolumeBeforeMute
                mp.setVolume(0)
            } else {
                mp.setVolume(savedVolumeBeforeMute)
            }
        }
    }

    override fun isMuted(): Boolean = (mediaPlayer?.getVolume() ?: 100) == 0

    override fun getSubtitleTracks(): List<SubtitleTrack> {
        val mp = mediaPlayer ?: return emptyList()
        val tracks = mp.getSpuTracks() ?: return emptyList()
        return tracks.map { SubtitleTrack(id = it.id.toString(), name = it.name ?: "Track ${it.id}", isEmbedded = true) }
    }

    override fun setSubtitleTrack(trackId: Int) {
        mediaPlayer?.setSpuTrack(trackId)
    }

    override fun getSubtitleTrack(): Int = mediaPlayer?.getSpuTrack() ?: -1

    override fun addExternalSubtitle(url: String): Int = -1

    override fun getAudioTracks(): List<AudioTrack> {
        val mp = mediaPlayer ?: return emptyList()
        val tracks = mp.getAudioTracks() ?: return emptyList()
        return tracks.map { AudioTrack(id = it.id.toString(), name = it.name ?: "Track ${it.id}") }
    }

    override fun setAudioTrack(trackId: Int) {
        mediaPlayer?.setAudioTrack(trackId)
    }

    override fun getAudioTrack(): Int = mediaPlayer?.getAudioTrack() ?: -1

    override fun setSubtitleDelay(delayMs: Long) {
        mediaPlayer?.setSpuDelay(delayMs)
    }

    override fun getSubtitleDelay(): Long = mediaPlayer?.getSpuDelay() ?: 0L
}
