package com.aitherapist.dls.data

import android.util.Log
import androidx.room.withTransaction
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

private const val TAG = "DlsData"

/** Child profiles. Deleting a child cascades its sessions and attempts and removes their frame folders. */
@Singleton
class ChildRepository
@Inject
internal constructor(private val holder: DatabaseHolder, private val frames: FrameStore) {
    fun observeChildren(): Flow<List<ChildEntity>> = flow { emitAll(holder.get().childDao().observeVisible()) }

    suspend fun get(id: String): ChildEntity? = holder.get().childDao().byId(id)

    suspend fun add(child: ChildEntity) = holder.get().childDao().insert(child)

    suspend fun update(child: ChildEntity) = holder.get().childDao().update(child)

    suspend fun delete(id: String) {
        val db = holder.get()
        val sessionIds =
            db.withTransaction {
                val ids = db.sessionDao().idsForChild(id)
                db.childDao().delete(id)
                ids
            }
        sessionIds.forEach { if (!frames.deleteSession(it)) Log.w(TAG, "Frame folder delete failed") }
    }
}

/** Sessions and attempts (written only by the assessment runner). */
@Singleton
class SessionRepository
@Inject
internal constructor(private val holder: DatabaseHolder, private val frames: FrameStore) {
    suspend fun add(session: SessionEntity) = holder.get().sessionDao().insert(session)

    suspend fun update(session: SessionEntity) = holder.get().sessionDao().update(session)

    suspend fun get(id: String): SessionEntity? = holder.get().sessionDao().byId(id)

    suspend fun forChild(childId: String): List<SessionEntity> = holder.get().sessionDao().forChild(childId)

    /** Deletes the session (attempts cascade) and then its frame folder. */
    suspend fun delete(id: String) {
        holder.get().sessionDao().delete(id)
        if (!frames.deleteSession(id)) Log.w(TAG, "Frame folder delete failed")
    }

    suspend fun addAttempt(attempt: AttemptEntity) = holder.get().attemptDao().insert(attempt)

    suspend fun attempts(sessionId: String): List<AttemptEntity> = holder.get().attemptDao().forSession(sessionId)

    suspend fun attempts(sessionId: String, stepId: String): List<AttemptEntity> = holder.get().attemptDao().forStep(sessionId, stepId)
}
