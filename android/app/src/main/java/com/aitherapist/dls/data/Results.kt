package com.aitherapist.dls.data

import android.util.Log
import com.aitherapist.dls.scoring.RuleAttempt
import com.aitherapist.dls.scoring.RuleOutcome
import com.aitherapist.dls.scoring.RulesEngine
import com.aitherapist.dls.scoring.StepLevel
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "DlsResults"

/**
 * One finished assessment session with its effective step levels (Story 4.1), recomputed from the saved attempts and
 * corrections on every read and never stored. [levels] maps every step with attempts or a correction to its level
 * (null = still open, e.g. a step without a closing row).
 */
data class SessionResult(val session: SessionEntity, val levels: Map<String, StepLevel?>)

/** Results of finished assessments (implemented by [ResultsRepository]; faked in unit tests). */
interface AssessmentResults {
    /** The child's finished (`done`) assessment sessions, newest first, optionally only [taskId]. */
    suspend fun results(childId: String, taskId: String? = null): List<SessionResult>

    /** The latest finished assessment per task (see [latestPerTask]); what reports and plans read. */
    suspend fun latestResults(childId: String): List<SessionResult> = latestPerTask(results(childId))
}

/** What the correction writer stores (AD-9: only the therapist Session detail screen calls it). */
interface CorrectionStore {
    /**
     * Saves the step's correction, replacing an earlier one of the same (sessionId, stepId) (upsert);
     * [CorrectionEntity.level] must be null (not scored) or 1..3.
     */
    suspend fun addCorrection(correction: CorrectionEntity)

    /** Deletes the step's corrections: it returns to the automatic level. */
    suspend fun clearCorrection(sessionId: String, stepId: String)
}

/**
 * Latest finished session per task: [ordered] must be newest first (startedAt DESC, then rowid DESC as tie-breaker,
 * as the DAO returns them); the first result of each taskId is kept. Jacket on and off are separate tasks, each kept.
 */
fun latestPerTask(ordered: List<SessionResult>): List<SessionResult> = ordered.distinctBy { it.session.taskId }

/** Rule 1 view of a saved attempt row. */
fun AttemptEntity.toRuleAttempt() = RuleAttempt(
    number = number,
    promptLevel = promptLevel,
    outcome =
    when (verdict) {
        AttemptVerdict.DONE -> RuleOutcome.DONE
        AttemptVerdict.NOT_DONE -> RuleOutcome.NOT_DONE
        AttemptVerdict.CANNOT_TELL -> RuleOutcome.CANNOT_TELL
    },
    verifierNone = verifier == VerifierKind.NONE
)

/**
 * Effective levels of one session from its [attempts] (saved order) and [corrections] (saved order; the latest per
 * step wins, and a correction, including null = not scored, wins over the automatic level).
 */
fun sessionLevels(
    attempts: List<AttemptEntity>,
    corrections: List<CorrectionEntity>,
    onInvalid: (String) -> Unit = {}
): Map<String, StepLevel?> {
    val valid =
        corrections.filter { row ->
            RulesEngine.isValidCorrection(row.level).also { ok -> if (!ok) onInvalid("invalid correction level ignored") }
        }
    val latest = valid.groupBy { it.stepId }.mapValues { (_, rows) -> latestCorrection(rows) }
    val byStep = attempts.groupBy { it.stepId }
    return (byStep.keys + latest.keys).associateWith { stepId ->
        val correction = latest[stepId]
        RulesEngine.effectiveStepLevel(
            byStep[stepId].orEmpty().map { it.toRuleAttempt() },
            hasCorrection = correction != null,
            correction = correction?.level
        )
    }
}

/**
 * The latest of one step's correction [rows], given in saved order (`ORDER BY createdAt, rowid` from the DAO):
 * highest createdAt wins, and on equal createdAt the later saved row (higher rowid, i.e. higher list index) wins.
 * Since v6 a step has at most one row; this keeps reads correct for any duplicates.
 */
fun latestCorrection(rows: List<CorrectionEntity>): CorrectionEntity =
    rows.withIndex().maxWith(compareBy<IndexedValue<CorrectionEntity>>({ it.value.createdAt }, { it.index })).value

/** Only finished assessment sessions count; replay, running, paused and abandoned sessions never do. */
fun countsForResults(session: SessionEntity): Boolean = session.kind == SessionKind.ASSESSMENT && session.state == SessionState.DONE

/** Recomputes results from the facts on every call (AD-5: nothing is cached or stored). */
@Singleton
class ResultsRepository
@Inject
internal constructor(private val holder: DatabaseHolder) :
    AssessmentResults,
    CorrectionStore {
    override suspend fun addCorrection(correction: CorrectionEntity) {
        require(RulesEngine.isValidCorrection(correction.level)) { "correction level must be null or 1..3" }
        holder.get().correctionDao().upsert(correction)
    }

    override suspend fun clearCorrection(sessionId: String, stepId: String) = holder.get().correctionDao().deleteFor(sessionId, stepId)

    override suspend fun results(childId: String, taskId: String?): List<SessionResult> {
        val db = holder.get()
        return db.sessionDao().finishedAssessments(childId, taskId).filter(::countsForResults).map { session ->
            SessionResult(
                session,
                // Type only, never child data.
                sessionLevels(db.attemptDao().forSession(session.id), db.correctionDao().forSession(session.id)) { msg ->
                    runCatching { Log.w(TAG, msg) }
                }
            )
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ResultsModule {
    @Binds
    abstract fun assessmentResults(impl: ResultsRepository): AssessmentResults

    @Binds
    abstract fun correctionStore(impl: ResultsRepository): CorrectionStore
}

/** Decrypted evidence frames for the therapist Session detail (AD-8): in memory only, never written decrypted. */
interface EvidenceFrames {
    /** JPEG bytes of the frame at [relativePath] (as stored in `attempt.framePaths`), or null when missing or unreadable. */
    suspend fun read(relativePath: String): ByteArray?
}

/** [EvidenceFrames] over [FrameStore]; a missing, tampered or foreign path reads as null and never throws. */
@Singleton
class FrameStoreEvidenceFrames
@Inject
internal constructor(private val frames: FrameStore) : EvidenceFrames {
    override suspend fun read(relativePath: String): ByteArray? = frames.readRelative(relativePath)
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class EvidenceFramesModule {
    @Binds
    abstract fun evidenceFrames(impl: FrameStoreEvidenceFrames): EvidenceFrames
}
