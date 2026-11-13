package com.aitherapist.dls.audio

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Plays one instruction mp3 from the APK assets at a time (Story 2.14). */
interface AudioPlayer {
    /**
     * Stops any current sound and plays [path] (relative to the assets root). Returns false when it cannot start
     * (missing file); then [onEnded] is never called. Otherwise [onEnded] is called once when the sound finishes
     * or fails mid-way, unless [stop] or another [play] came first.
     */
    fun play(path: String, onEnded: () -> Unit): Boolean

    /** Stops the current sound; its [play] callback is not called. */
    fun stop()
}

/** [AudioPlayer] on the platform [MediaPlayer]; the caller uses it from the main thread. */
class AssetAudioPlayer
@Inject
constructor(@ApplicationContext private val context: Context) : AudioPlayer {
    private var player: MediaPlayer? = null

    override fun play(path: String, onEnded: () -> Unit): Boolean {
        stop()
        val media = MediaPlayer()
        return try {
            context.assets.openFd(path).use { fd -> media.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length) }
            media.setOnCompletionListener { finish(it, onEnded) }
            media.setOnErrorListener { mp, _, _ ->
                finish(mp, onEnded)
                true
            }
            // Prepared off the main thread; a player replaced or stopped meanwhile is never started.
            media.setOnPreparedListener { mp -> if (player === mp) mp.start() }
            player = media
            media.prepareAsync()
            true
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception
        ) {
            Log.w(TAG, "audio unavailable: ${e::class.simpleName}") // type only, no path or data
            if (player === media) player = null
            media.release()
            false
        }
    }

    private fun finish(mp: MediaPlayer, onEnded: () -> Unit) {
        if (player !== mp) return
        player = null
        mp.release()
        onEnded()
    }

    override fun stop() {
        player?.let {
            player = null
            it.setOnCompletionListener(null)
            it.setOnErrorListener(null)
            it.setOnPreparedListener(null)
            runCatching { it.stop() }
            it.release()
        }
    }

    private companion object {
        const val TAG = "DlsAudio"
    }
}
