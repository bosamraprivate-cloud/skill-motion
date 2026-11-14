package com.aitherapist.dls.rewards

/**
 * How a step (or a training repetition set) ended, as far as rewards care (design "Rewards and effects").
 * Stars reward taking part and finishing, never the level.
 */
sealed interface Finish {
    /**
     * Done. [firstTry] mirrors Rule 1's level 3: no earlier attempt was not done and the done attempt was at prompt
     * level 1 (a done on the cannot-tell repeat still counts as first try).
     */
    data class Done(val firstTry: Boolean) : Finish

    /** Finished without being done (not done after attempt 3 with Done), not scored, or content-only. */
    data object Finished : Finish
}

/** Pure reward rules (design "Rewards and effects", Story 4.7). No Android, Room or coroutine imports. */
object RewardRules {
    /** Tasks finished per sticker unlock. */
    const val TASKS_PER_STICKER = 3

    private const val BASE_STARS = 1
    private const val FIRST_TRY_BONUS = 1

    /** 1 star for any finished step, plus a bonus star when done on attempt 1: 2 / 1 / 1. */
    fun stars(finish: Finish): Int = when (finish) {
        is Finish.Done -> if (finish.firstTry) BASE_STARS + FIRST_TRY_BONUS else BASE_STARS
        Finish.Finished -> BASE_STARS
    }

    /** Stickers unlocked after [finishedTasks] finished tasks (3/6/9 -> 1/2/3). */
    fun stickersUnlocked(finishedTasks: Int): Int = finishedTasks.coerceAtLeast(0) / TASKS_PER_STICKER

    /** Whether finishing task number [finishedTasks] unlocks a new sticker. */
    fun unlocksSticker(finishedTasks: Int): Boolean = finishedTasks > 0 && finishedTasks % TASKS_PER_STICKER == 0

    /** Finished tasks still needed for the next sticker (1..3). */
    fun tasksToNextSticker(finishedTasks: Int): Int = TASKS_PER_STICKER - finishedTasks.coerceAtLeast(0) % TASKS_PER_STICKER
}
