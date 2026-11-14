package com.aitherapist.dls.data

import android.database.sqlite.SQLiteConstraintException
import android.util.Log
import androidx.room.withTransaction
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

private const val TAG = "DlsData"

private const val CODE_PREFIX = "C-"
private const val CODE_DIGITS = 3

/**
 * Next background child code: "C-" + zero-padded sequence, one above the highest numeric suffix in [existing]
 * (codes that are not "C-<digits>" are ignored). Used later for the anonymised export; never shown in the form.
 */
fun nextChildCode(existing: Collection<String>): String {
    val highest =
        existing
            .filter { it.startsWith(CODE_PREFIX) }
            .map { it.substring(CODE_PREFIX.length) }
            .filter { it.isNotEmpty() && it.all(Char::isDigit) }
            .mapNotNull { it.toLongOrNull() }
            .maxOrNull() ?: 0L
    return CODE_PREFIX + (highest + 1).toString().padStart(CODE_DIGITS, '0')
}

/** Child profiles as the therapist screens see them (implemented by [ChildRepository]; faked in unit tests). */
interface ChildProfiles {
    /** Non-eval children ordered by name. */
    fun observeChildren(): Flow<List<ChildEntity>>

    suspend fun get(id: String): ChildEntity?

    /** Inserts [child] as given (its code included). */
    suspend fun add(child: ChildEntity)

    /** Inserts [child] with a generated unique code (see [nextChildCode]), ignoring its code; returns the stored row. */
    suspend fun addWithGeneratedCode(child: ChildEntity): ChildEntity

    /** Updates the row; callers keep the stored code (it never changes on edit). */
    suspend fun update(child: ChildEntity)

    /** Deletes the child, cascading its sessions and attempts, then removes their frame folders. */
    suspend fun delete(id: String)
}

/** Child profiles. Deleting a child cascades its sessions and attempts and removes their frame folders. */
@Singleton
class ChildRepository
@Inject
internal constructor(private val holder: DatabaseHolder, private val frames: FrameStore) : ChildProfiles {
    override fun observeChildren(): Flow<List<ChildEntity>> = flow { emitAll(holder.get().childDao().observeVisible()) }

    override suspend fun get(id: String): ChildEntity? = holder.get().childDao().byId(id)

    override suspend fun add(child: ChildEntity) = holder.get().childDao().insert(child)

    override suspend fun update(child: ChildEntity) = holder.get().childDao().update(child)

    override suspend fun addWithGeneratedCode(child: ChildEntity): ChildEntity {
        val dao = holder.get().childDao()
        val first = child.copy(code = nextChildCode(dao.generatedCodes()))
        return try {
            first.also { dao.insert(it) }
        } catch (_: SQLiteConstraintException) {
            // A concurrent insert took the code: generate once more.
            child.copy(code = nextChildCode(dao.generatedCodes())).also { dao.insert(it) }
        }
    }

    override suspend fun delete(id: String) {
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

/** What the assessment runner writes and reads (implemented by [SessionRepository]; faked in unit tests). */
interface SessionStore {
    suspend fun add(session: SessionEntity)

    suspend fun update(session: SessionEntity)

    suspend fun addAttempt(attempt: AttemptEntity)

    /** Attempts of one step in saved order. */
    suspend fun attempts(sessionId: String, stepId: String): List<AttemptEntity>

    /** The child's running or paused sessions, newest first (at most one is expected). */
    suspend fun resumable(childId: String): List<SessionEntity>
}

/** Interrupted sessions as the therapist screens and child mode read them (implemented by [SessionRepository]). */
interface ResumableSessions {
    /** Every running or paused session, newest first. */
    fun observeResumable(): Flow<List<SessionEntity>>

    suspend fun session(id: String): SessionEntity?

    /** Whether [childId] finished an assessment of [taskId] that started after [since] (a chained task already done). */
    suspend fun finishedSince(childId: String, taskId: String, since: Long): Boolean = false
}

/** Sessions and attempts (written only by the assessment runner). */
@Singleton
class SessionRepository
@Inject
internal constructor(private val holder: DatabaseHolder, private val frames: FrameStore) :
    SessionStore,
    ResumableSessions {
    override suspend fun add(session: SessionEntity) = holder.get().sessionDao().insert(session)

    override suspend fun update(session: SessionEntity) = holder.get().sessionDao().update(session)

    suspend fun get(id: String): SessionEntity? = holder.get().sessionDao().byId(id)

    override suspend fun session(id: String): SessionEntity? = get(id)

    override suspend fun finishedSince(childId: String, taskId: String, since: Long): Boolean =
        holder.get().sessionDao().finishedSince(childId, taskId, since)

    override fun observeResumable(): Flow<List<SessionEntity>> = flow { emitAll(holder.get().sessionDao().observeResumable()) }

    override suspend fun resumable(childId: String): List<SessionEntity> = holder.get().sessionDao().resumableForChild(childId)

    suspend fun forChild(childId: String): List<SessionEntity> = holder.get().sessionDao().forChild(childId)

    /** Deletes the session (attempts cascade) and then its frame folder. */
    suspend fun delete(id: String) {
        holder.get().sessionDao().delete(id)
        if (!frames.deleteSession(id)) Log.w(TAG, "Frame folder delete failed")
    }

    override suspend fun addAttempt(attempt: AttemptEntity) = holder.get().attemptDao().insert(attempt)

    suspend fun attempts(sessionId: String): List<AttemptEntity> = holder.get().attemptDao().forSession(sessionId)

    override suspend fun attempts(sessionId: String, stepId: String): List<AttemptEntity> =
        holder.get().attemptDao().forStep(sessionId, stepId)
}

/** Reward rows as `rewards/RewardRecorder` writes and reads them (implemented by [RewardRepository]; faked in tests). */
interface RewardStore {
    /** Inserts [reward] unless a row with its id exists; true when inserted. */
    suspend fun addIfAbsent(reward: RewardEntity): Boolean

    /** Whether [childId] is the hidden eval child (or missing): such children earn nothing. */
    suspend fun isEvalOrMissing(childId: String): Boolean

    suspend fun starsForSession(sessionId: String): Int

    /** Finished (`done`) assessment sessions plus rewarded training sets of [childId]. */
    suspend fun finishedTasks(childId: String): Int
}

/** Live star count of one session for the child star bar. */
fun interface SessionStars {
    fun observe(sessionId: String): Flow<Int>
}

/** Reward rows (written only through `rewards/RewardRecorder`). */
@Singleton
class RewardRepository
@Inject
internal constructor(private val holder: DatabaseHolder) :
    RewardStore,
    SessionStars {
    override suspend fun addIfAbsent(reward: RewardEntity): Boolean = holder.get().rewardDao().insertIfAbsent(reward) != -1L

    override suspend fun isEvalOrMissing(childId: String): Boolean = holder.get().childDao().byId(childId)?.isEval ?: true

    override suspend fun starsForSession(sessionId: String): Int = holder.get().rewardDao().starsForSession(sessionId)

    override suspend fun finishedTasks(childId: String): Int {
        val db = holder.get()
        return db.sessionDao().finishedAssessmentCount(childId) + db.rewardDao().trainingSets(childId)
    }

    override fun observe(sessionId: String): Flow<Int> = flow { emitAll(holder.get().rewardDao().observeStarsForSession(sessionId)) }
}

/** Training result rows as `training/TrainingRunner` writes them (implemented by [TrainingResultRepository]; faked in tests). */
interface TrainingResultStore {
    suspend fun add(result: TrainingResultEntity)

    /** The saved row with [id], if any (a restored run skips it). */
    suspend fun find(id: String): TrainingResultEntity?
}

/** `training_result` rows (written only through `training/TrainingRunner`, AD-9). */
@Singleton
class TrainingResultRepository
@Inject
internal constructor(private val holder: DatabaseHolder) : TrainingResultStore {
    override suspend fun add(result: TrainingResultEntity) = holder.get().trainingResultDao().insert(result)

    override suspend fun find(id: String): TrainingResultEntity? = holder.get().trainingResultDao().byId(id)

    suspend fun forChild(childId: String): List<TrainingResultEntity> = holder.get().trainingResultDao().forChild(childId)
}
