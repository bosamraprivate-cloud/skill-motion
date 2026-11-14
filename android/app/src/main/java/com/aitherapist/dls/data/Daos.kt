package com.aitherapist.dls.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
internal interface ChildDao {
    @Insert suspend fun insert(child: ChildEntity)

    @Update suspend fun update(child: ChildEntity)

    @Query("SELECT * FROM child WHERE id = :id")
    suspend fun byId(id: String): ChildEntity?

    @Query("SELECT * FROM child WHERE isEval = 0 ORDER BY name")
    fun observeVisible(): Flow<List<ChildEntity>>

    /** The hidden eval child that owns replay sessions (Story 3.7), if created. */
    @Query("SELECT * FROM child WHERE isEval = 1 ORDER BY createdAt LIMIT 1")
    suspend fun evalChild(): ChildEntity?

    @Query("SELECT code FROM child WHERE code LIKE 'C-%'")
    suspend fun generatedCodes(): List<String>

    @Query("DELETE FROM child WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
internal interface SessionDao {
    @Insert suspend fun insert(session: SessionEntity)

    @Update suspend fun update(session: SessionEntity)

    @Query("SELECT * FROM session WHERE id = :id")
    suspend fun byId(id: String): SessionEntity?

    /** The child's assessment sessions (replay sessions never feed reports). */
    @Query("SELECT * FROM session WHERE childId = :childId AND kind = 'assessment' ORDER BY startedAt DESC")
    suspend fun forChild(childId: String): List<SessionEntity>

    /** The child's finished (`done`) assessment sessions, newest first; optionally one task only (Story 4.1). */
    @Query(
        "SELECT * FROM session WHERE childId = :childId AND kind = 'assessment' AND state = 'done' " +
            "AND (:taskId IS NULL OR taskId = :taskId) ORDER BY startedAt DESC, rowid DESC"
    )
    suspend fun finishedAssessments(childId: String, taskId: String?): List<SessionEntity>

    @Query("SELECT COUNT(*) FROM session WHERE childId = :childId AND kind = 'assessment' AND state = 'done'")
    suspend fun finishedAssessmentCount(childId: String): Int

    /** Whether the child has a finished assessment of [taskId] that started after [since] (jacket chain, Story 4.1). */
    @Query(
        "SELECT EXISTS(SELECT 1 FROM session WHERE childId = :childId AND taskId = :taskId AND kind = 'assessment' " +
            "AND state = 'done' AND startedAt > :since)"
    )
    suspend fun finishedSince(childId: String, taskId: String, since: Long): Boolean

    /** Running or paused assessment sessions of visible children (resumable), newest first; replay and the eval child never show. */
    @Query(
        "SELECT * FROM session WHERE state IN ('running', 'paused') AND kind = 'assessment' " +
            "AND childId NOT IN (SELECT id FROM child WHERE isEval = 1) ORDER BY startedAt DESC"
    )
    fun observeResumable(): Flow<List<SessionEntity>>

    @Query(
        "SELECT * FROM session WHERE childId = :childId AND state IN ('running', 'paused') AND kind = 'assessment' ORDER BY startedAt DESC"
    )
    suspend fun resumableForChild(childId: String): List<SessionEntity>

    /** Replay sessions (Story 3.7): finished (`done`) ones first, then newest first. */
    @Query("SELECT * FROM session WHERE kind = 'replay' ORDER BY CASE WHEN state = 'done' THEN 0 ELSE 1 END, startedAt DESC")
    suspend fun replaySessions(): List<SessionEntity>

    @Query("SELECT id FROM session WHERE childId = :childId")
    suspend fun idsForChild(childId: String): List<String>

    @Query("DELETE FROM session WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
internal interface AttemptDao {
    @Insert suspend fun insert(attempt: AttemptEntity)

    @Query("SELECT * FROM attempt WHERE sessionId = :sessionId ORDER BY createdAt, number")
    suspend fun forSession(sessionId: String): List<AttemptEntity>

    @Query("SELECT * FROM attempt WHERE sessionId = :sessionId AND stepId = :stepId ORDER BY createdAt, number")
    suspend fun forStep(sessionId: String, stepId: String): List<AttemptEntity>

    @Query("DELETE FROM attempt WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
internal interface CorrectionDao {
    @Insert suspend fun insert(correction: CorrectionEntity)

    /** Saves the step's correction; REPLACE drops the step's previous row through the unique (sessionId, stepId) index. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(correction: CorrectionEntity)

    /** Corrections of [sessionId] in saved order (the last per step wins). */
    @Query("SELECT * FROM correction WHERE sessionId = :sessionId ORDER BY createdAt, rowid")
    suspend fun forSession(sessionId: String): List<CorrectionEntity>

    @Query("DELETE FROM correction WHERE sessionId = :sessionId AND stepId = :stepId")
    suspend fun deleteFor(sessionId: String, stepId: String)
}

@Dao
internal interface RewardDao {
    /** Returns -1 when a row with the same id exists (idempotent per session step / training result). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(reward: RewardEntity): Long

    @Query("SELECT COALESCE(SUM(stars), 0) FROM reward WHERE sessionId = :sessionId")
    suspend fun starsForSession(sessionId: String): Int

    @Query("SELECT COALESCE(SUM(stars), 0) FROM reward WHERE sessionId = :sessionId")
    fun observeStarsForSession(sessionId: String): Flow<Int>

    /** Finished training runs: one zero-star task row (`task:run:<runId>`) per run. */
    @Query("SELECT COUNT(*) FROM reward WHERE childId = :childId AND id LIKE 'task:run:%'")
    suspend fun finishedRuns(childId: String): Int

    /** Stars of the rows whose id starts with [prefix] (one session's steps or one training run's sets). */
    @Query("SELECT COALESCE(SUM(stars), 0) FROM reward WHERE id LIKE :prefix || '%'")
    suspend fun starsForPrefix(prefix: String): Int

    /** Every reward row of [childId] (read-only session history, Epic 4 UI pass). */
    @Query("SELECT * FROM reward WHERE childId = :childId ORDER BY createdAt, id")
    suspend fun forChild(childId: String): List<RewardEntity>

    /** Stickers [childId] has unlocked, in unlock order. */
    @Query("SELECT stickerId FROM reward WHERE childId = :childId AND stickerId IS NOT NULL ORDER BY createdAt, id")
    suspend fun stickerIds(childId: String): List<String>

    @Query("SELECT stickerId FROM reward WHERE id = :id")
    suspend fun stickerOf(id: String): String?

    /** Sets the sticker of row [id] only when it has none; returns the rows changed (0 or 1). */
    @Query("UPDATE reward SET stickerId = :stickerId WHERE id = :id AND stickerId IS NULL")
    suspend fun setStickerIfAbsent(id: String, stickerId: String): Int
}

@Dao
internal interface TrainingResultDao {
    @Insert suspend fun insert(result: TrainingResultEntity)

    @Query("SELECT * FROM training_result WHERE id = :id")
    suspend fun byId(id: String): TrainingResultEntity?

    @Query("SELECT * FROM training_result WHERE childId = :childId ORDER BY createdAt DESC, rowid DESC")
    suspend fun forChild(childId: String): List<TrainingResultEntity>
}
