package com.disone.core.player

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
}
