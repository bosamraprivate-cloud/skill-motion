package com.aitherapist.dls.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/** Cannot-tell counts over saved assessment attempts (verifier `none` and the eval child excluded). */
data class CannotTellCounts(val total: Int, val cannotTell: Int) {
    /** Whole percent of cannot-tell attempts, or null when there are no attempts yet. */
    val percent: Int? get() = if (total <= 0) null else (cannotTell * PERCENT + total / 2) / total

    private companion object {
        const val PERCENT = 100
    }
}

/** Attempt statistics for the therapist Diagnostics screen (Story 5.5). */
fun interface AttemptStats {
    suspend fun cannotTellCounts(): CannotTellCounts
}

@Singleton
class AttemptStatsRepository
@Inject
internal constructor(private val holder: DatabaseHolder) : AttemptStats {
    override suspend fun cannotTellCounts(): CannotTellCounts = holder.get().attemptDao().cannotTellCounts()
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class AttemptStatsModule {
    @Binds
    abstract fun attemptStats(impl: AttemptStatsRepository): AttemptStats
}
