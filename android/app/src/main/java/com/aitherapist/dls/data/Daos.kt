package com.aitherapist.dls.data

import androidx.room.Dao
import androidx.room.Insert
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
