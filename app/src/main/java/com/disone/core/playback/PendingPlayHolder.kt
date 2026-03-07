package com.disone.core.playback

import com.disone.core.addons.DisoneStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds a pending stream to play when navigating to Player.
 * Used for addon streams since we cannot easily pass complex objects via NavArgs.
 */
@Singleton
class PendingPlayHolder @Inject constructor() {

    @Volatile
    private var pending: DisoneStream? = null

    fun setPending(stream: DisoneStream) {
        pending = stream
    }

    fun takePending(): DisoneStream? {
        val s = pending
        pending = null
        return s
    }

    fun hasPending(): Boolean = pending != null
}
