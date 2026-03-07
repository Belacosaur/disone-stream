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

    override fun setRate(rate: Float) {
        exoPlayer?.setPlaybackSpeed(rate.coerceIn(0.25f, 4f))
    }

    override fun getRate(): Float = exoPlayer?.playbackParameters?.speed ?: 1f

    override fun setVolume(volume: Int) {
        exoPlayer?.volume = (volume / 100f).coerceIn(0f, 1f)
    }

    override fun getVolume(): Int = ((exoPlayer?.volume ?: 1f) * 100).toInt()

    override fun setMuted(muted: Boolean) {
        exoPlayer?.volume = if (muted) 0f else 1f
    }

    override fun isMuted(): Boolean = (exoPlayer?.volume ?: 1f) == 0f

    override fun getSubtitleTracks(): List<SubtitleTrack> = emptyList()

    override fun setSubtitleTrack(trackId: Int) {}

    override fun getSubtitleTrack(): Int = -1

    override fun addExternalSubtitle(url: String): Int = -1

    override fun getAudioTracks(): List<AudioTrack> = emptyList()

    override fun setAudioTrack(trackId: Int) {}

    override fun getAudioTrack(): Int = -1

    override fun setSubtitleDelay(delayMs: Long) {}

    override fun getSubtitleDelay(): Long = 0L

    fun bindToView(playerView: PlayerView) {
        exoPlayer?.let { playerView.player = it }
    }

    fun getPlayer(): Player? = exoPlayer
}
