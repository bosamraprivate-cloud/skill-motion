package com.aitherapist.dls.ui.child

import com.aitherapist.dls.data.EffectLevel

/**
 * Reward sound asset paths, kept in one place (Story 4.8). The sfx are not generated yet (Story 2.6 deferred):
 * a missing file plays nothing (silent fallback), so dropping the files in later needs no code change.
 */
object RewardSounds {
    const val CHIME = "audio/sfx/chime.mp3"
    const val SOFT = "audio/sfx/soft.mp3"

    /** The step-done sound for [level]; encouragement plays no sound (never a negative one). */
    fun forCelebration(level: EffectLevel): String = if (level == EffectLevel.FULL) CHIME else SOFT
}
