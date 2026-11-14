package com.aitherapist.dls.rewards

import java.util.Locale

/**
 * Sticker ids (Story 4.9). The n-th unlocked sticker (0-based) is `sticker.NN` with NN = n + 1; ids are never reused.
 * Sticker art does not exist yet (Story 2.6 deferred): `ui/child/StickerArt` draws placeholder badges by index, so real
 * art can replace them later without code changes elsewhere.
 */
object Stickers {
    /** Slots the sticker book always shows (more appear once earned). */
    const val BOOK_SLOTS = 30

    fun idFor(index: Int): String = String.format(Locale.ROOT, "sticker.%02d", index.coerceAtLeast(0) + 1)

    /** 0-based index of [stickerId], or null when it is not a sticker id. */
    fun indexOf(stickerId: String): Int? = stickerId.removePrefix("sticker.").toIntOrNull()?.minus(1)?.takeIf { it >= 0 }
}

/** The task reward: stars of the task, finished tasks so far and the sticker this task unlocked (if any). */
data class TaskReward(val stars: Int, val finishedTasks: Int, val newSticker: String?)
