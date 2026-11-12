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

    @Query("SELECT EXISTS(SELECT 1 FROM child WHERE code = :code AND id != :excludeId)")
    suspend fun codeExists(code: String, excludeId: String): Boolean

    @Query("DELETE FROM child WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
internal interface SessionDao {
    @Insert suspend fun insert(session: SessionEntity)

    @Update suspend fun update(session: SessionEntity)

    @Query("SELECT * FROM session WHERE id = :id")
    suspend fun byId(id: String): SessionEntity?

    @Query("SELECT * FROM session WHERE childId = :childId ORDER BY startedAt DESC")
    suspend fun forChild(childId: String): List<SessionEntity>

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
