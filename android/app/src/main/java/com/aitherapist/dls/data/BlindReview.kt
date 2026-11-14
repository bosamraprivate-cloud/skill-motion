package com.aitherapist.dls.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One attempt as the blind reviewer may see it (Story 5.7): the step id, the frames that exist on disk and the
 * replay clip (app-private file, never shown by name). Never the app's verdict, the session or the child.
 */
data class BlindItem(val attemptId: String, val stepId: String, val framePaths: List<String>, val clip: File? = null)

/**
 * Blind ratings (Story 5.7, AD-9): the only writer is the Blind review screen's ViewModel. Ratings never feed
 * levels, reports, plans or compare. Implemented by [BlindReviewRepository]; faked in unit tests.
 */
interface BlindReview {
    /**
     * Attempts of assessment and replay sessions (eval child included), in saved order, with only the frames that
     * exist on disk; an attempt with no existing frame and no existing clip is left out.
     */
    suspend fun candidates(): List<BlindItem>

    /** Ids of attempts that already have a blind rating. */
    suspend fun ratedAttemptIds(): Set<String>

    /** Saves the attempt's rating, replacing an earlier one (upsert on attemptId). */
    suspend fun rate(rating: BlindRatingEntity)

    /** Whether the attempt still exists (it may have been deleted with its session or child). */
    suspend fun attemptExists(attemptId: String): Boolean
}

@Singleton
class BlindReviewRepository
@Inject
internal constructor(
    private val holder: DatabaseHolder,
    private val frames: FrameStore,
    private val clips: ClipFiles
) : BlindReview {
    override suspend fun candidates(): List<BlindItem> = holder.get().blindRatingDao().candidates().mapNotNull { row ->
        val existing = row.framePaths.filter(frames::existsRelative)
        val clip = row.clipFile?.let { name -> runCatching { clips.file(name) }.getOrNull()?.takeIf { it.isFile } }
        if (existing.isEmpty() && clip == null) null else BlindItem(row.attemptId, row.stepId, existing, clip)
    }

    override suspend fun ratedAttemptIds(): Set<String> = holder.get().blindRatingDao().ratedAttemptIds().toSet()

    override suspend fun rate(rating: BlindRatingEntity) = holder.get().blindRatingDao().upsert(rating)

    override suspend fun attemptExists(attemptId: String): Boolean = holder.get().blindRatingDao().attemptExists(attemptId)
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class BlindReviewModule {
    @Binds
    abstract fun blindReview(impl: BlindReviewRepository): BlindReview
}
