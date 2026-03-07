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
    private var onPlaybackError: ((String) -> Unit)? = null
    private val mainHandler = Handler(Looper.getMainLooper())

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
            mediaPlayer?.attachViews(layout, null, true, false)
            pendingVideoLayout = null
        }
    }

    fun setOnPlaybackError(callback: ((String) -> Unit)?) {
        onPlaybackError = callback
    }

    private fun createPlayerWithErrorListener(vlc: LibVLC): MediaPlayer {
        return MediaPlayer(vlc).apply {
            setEventListener(object : MediaPlayer.EventListener {
                override fun onEvent(event: MediaPlayer.Event) {
                    if (event.type == MediaPlayer.Event.EncounteredError) {
                        val cb = onPlaybackError
                        if (cb != null) {
                            mainHandler.post { cb("Stream unavailable or playback failed") }
                        }
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
        pendingVideoLayout = layout
        mediaPlayer?.let {
            // Detach before re-attach to avoid "Can't set view when already attached" (Compose recomposition)
            it.detachViews()
            it.attachViews(layout, null, true, false)
            pendingVideoLayout = null
        }
    }

    fun detachViews() {
        mediaPlayer?.detachViews()
    }
}
