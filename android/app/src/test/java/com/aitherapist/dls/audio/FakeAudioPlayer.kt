package com.aitherapist.dls.audio

/** In-memory [AudioPlayer]: [available] decides whether a path can start; [finish] ends the current sound. */
class FakeAudioPlayer(var available: (String) -> Boolean = { true }) : AudioPlayer {
    val played = mutableListOf<String>()
    var stops = 0
    private var current: (() -> Unit)? = null

    val playing: Boolean get() = current != null

    override fun play(path: String, onEnded: () -> Unit): Boolean {
        stop()
        if (!available(path)) return false
        played += path
        current = onEnded
        return true
    }

    override fun stop() {
        if (current != null) stops++
        current = null
    }

    /** The current sound reaches its end. */
    fun finish() {
        val callback = current ?: return
        current = null
        callback()
    }
}
