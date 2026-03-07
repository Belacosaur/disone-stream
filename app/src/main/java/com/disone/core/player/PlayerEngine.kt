package com.disone.core.player

/** Subtitle track info (embedded or external/addon) */
data class SubtitleTrack(
    val id: String,
    val name: String,
    val lang: String? = null,
    val isEmbedded: Boolean = true,
    /** For addon/external tracks: URL to load (e.g. .srt from OpenSubtitles) */
    val url: String? = null
)

/** Audio track info */
data class AudioTrack(val id: String, val name: String, val lang: String? = null)

interface PlayerEngine {
    fun playLocal(path: String)
    fun playUrl(url: String)
    fun stop()
    fun release()

    /** Pause playback */
    fun pause()

    /** Resume playback */
    fun play()

    /** True if currently playing (not paused/stopped) */
    fun isPlaying(): Boolean

    /** Current position in milliseconds. -1 if unknown. */
    fun getTime(): Long

    /** Total duration in milliseconds. -1 if unknown (e.g. live stream). */
    fun getLength(): Long

    /** Seek to position in milliseconds */
    fun seekTo(timeMs: Long)

    // --- Stremio-style controls ---

    /** Playback rate (0.25 .. 4.0, 1.0 = normal). Default 1.0. */
    fun setRate(rate: Float)
    fun getRate(): Float

    /** Volume 0-100. */
    fun setVolume(volume: Int)
    fun getVolume(): Int

    /** Mute / unmute. */
    fun setMuted(muted: Boolean)
    fun isMuted(): Boolean

    /** Embedded subtitle tracks. Empty until media is parsed/playing. */
    fun getSubtitleTracks(): List<SubtitleTrack>

    /** Selected subtitle track id, or null if off. Use -1 for libVLC "off". */
    fun setSubtitleTrack(trackId: Int)
    fun getSubtitleTrack(): Int

    /** Add external subtitle from URL (srt, vtt, etc). Returns track id or -1. */
    fun addExternalSubtitle(url: String): Int

    /** Audio tracks. Empty until media is parsed/playing. */
    fun getAudioTracks(): List<AudioTrack>

    /** Selected audio track id. */
    fun setAudioTrack(trackId: Int)
    fun getAudioTrack(): Int

    /** Subtitle delay in ms (for external subs). */
    fun setSubtitleDelay(delayMs: Long)
    fun getSubtitleDelay(): Long
}
