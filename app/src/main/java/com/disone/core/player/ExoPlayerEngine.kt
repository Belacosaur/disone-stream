package com.disone.core.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExoPlayerEngine @Inject constructor(
    private val context: Context
) : PlayerEngine {

    private var exoPlayer: ExoPlayer? = null

    override fun playLocal(path: String) {
        stop()
        exoPlayer = ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri("file://$path"))
            prepare()
            playWhenReady = true
        }
    }

    override fun playUrl(url: String) {
        stop()
        exoPlayer = ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
        }
    }

    override fun stop() {
        exoPlayer?.release()
        exoPlayer = null
    }

    override fun release() {
        stop()
    }

    override fun pause() {
        exoPlayer?.pause()
    }

    override fun play() {
        exoPlayer?.play()
    }

    override fun isPlaying(): Boolean = exoPlayer?.playWhenReady == true && exoPlayer?.playbackState == Player.STATE_READY

    override fun getTime(): Long = exoPlayer?.currentPosition ?: -1L

    override fun getLength(): Long {
        val duration = exoPlayer?.duration ?: return -1L
        return if (duration > 0) duration else -1L
    }

    override fun seekTo(timeMs: Long) {
        exoPlayer?.seekTo(timeMs)
    }

    fun bindToView(playerView: PlayerView) {
        exoPlayer?.let { playerView.player = it }
    }

    fun getPlayer(): Player? = exoPlayer
}
