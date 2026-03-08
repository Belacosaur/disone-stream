package com.disone.core.playback

import com.disone.core.addons.DisoneStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds a pending stream to play when navigating to Player.
 * Used for addon streams since we cannot easily pass complex objects via NavArgs.
 * Optionally holds startPositionMs for resume-from-saved-progress.
 */
@Singleton
class PendingPlayHolder @Inject constructor() {

    @Volatile
    private var pending: DisoneStream? = null

    @Volatile
    private var startPositionMs: Long = 0L

    fun setPending(stream: DisoneStream, resumeFromMs: Long = 0L) {
        pending = stream
        startPositionMs = resumeFromMs
    }

    /** Peek without consuming — for access check before play. */
    fun peekPending(): DisoneStream? = pending

    fun takePending(): Pair<DisoneStream?, Long> {
        val s = pending
        val pos = startPositionMs
        pending = null
        startPositionMs = 0L
        return (s to pos)
    }

    fun hasPending(): Boolean = pending != null
}
