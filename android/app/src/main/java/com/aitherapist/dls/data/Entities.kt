package com.aitherapist.dls.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Child profile (design §Data model). The hidden eval child has [isEval] = true. */
@Entity(tableName = "child", indices = [Index(value = ["code"], unique = true)])
data class ChildEntity(
    @PrimaryKey val id: String,
    val name: String,
    val code: String,
    val ageYears: Int,
    @ColumnInfo(defaultValue = "calm") val effectLevel: EffectLevel = EffectLevel.CALM,
    val characterVariant: ChildCharacter,
    @ColumnInfo(defaultValue = "0") val isEval: Boolean = false,
    val createdAt: Long
)

@Entity(
    tableName = "session",
    foreignKeys = [
        ForeignKey(entity = ChildEntity::class, parentColumns = ["id"], childColumns = ["childId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("childId")]
)
data class SessionEntity(
    @PrimaryKey val id: String,
    val childId: String,
    val taskId: String,
    val kind: SessionKind,
    val state: SessionState,
    val startedAt: Long,
    val endedAt: Long?,
    val catalogVersion: String,
    val rulesVersion: String,
    val stepIndex: Int,
    val attemptNumber: Int,
    val promptLevel: Int,
    /** Jacket "both" (Story 2.16): the task chained after this one, kept so Continue still runs it (Story 4.1). */
    val nextTaskId: String? = null
)

@Entity(
    tableName = "attempt",
    foreignKeys = [
        ForeignKey(entity = SessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["sessionId", "stepId"])]
)
data class AttemptEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val stepId: String,
    val number: Int,
    val promptLevel: Int,
    val helpPressed: Boolean,
    val verdict: AttemptVerdict,
    val reason: String,
    val verifier: VerifierKind,
    val modelId: String?,
    val promptVersion: String?,
    val latencyMs: Int,
    val framePaths: List<String>,
    val clipFile: String?,
    val createdAt: Long,
    /** The model's short free text when the verdict came from the model (reason is then `model`); never logged. */
    val modelReason: String? = null
)

/**
 * Therapist correction of one step's level in one session (AD-9, Story 4.1). [level] 0..3 is a score, null means
 * "not scored". One row per (sessionId, stepId) (unique since v6, Story 5.2); it wins over the automatic Rule 1 level.
 */
@Entity(
    tableName = "correction",
    foreignKeys = [
        ForeignKey(entity = SessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["sessionId", "stepId"], unique = true)]
)
data class CorrectionEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val stepId: String,
    val level: Int?,
    val reason: String? = null,
    val createdAt: Long
)

/**
 * Stars earned in child mode (AD-9, Story 4.7); written only by `rewards/RewardRecorder`. [id] is deterministic per
 * (session, step) or per training result, so a retried or restored write is ignored. [sessionId] is null for a
 * training set. [stickerId] is assigned in Story 4.9 (null until then). Rewards never feed scoring.
 */
@Entity(
    tableName = "reward",
    foreignKeys = [
        ForeignKey(entity = ChildEntity::class, parentColumns = ["id"], childColumns = ["childId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("childId"), Index("sessionId")]
)
data class RewardEntity(
    @PrimaryKey val id: String,
    val childId: String,
    val sessionId: String?,
    val stars: Int,
    val stickerId: String? = null,
    val createdAt: Long
)

/**
 * One finished training activity (Story 4.6, AD-9); written only by `training/TrainingRunner`. [count] is the camera
 * count (null for a guided activity); [criterionMet] is false whenever the camera could not confirm the criterion
 * (doubt is never failure: it costs no stars). An interrupted activity writes no row.
 */
@Entity(
    tableName = "training_result",
    foreignKeys = [
        ForeignKey(entity = ChildEntity::class, parentColumns = ["id"], childColumns = ["childId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("childId")]
)
data class TrainingResultEntity(
    @PrimaryKey val id: String,
    val childId: String,
    val activityId: String,
    val count: Int?,
    val durationSec: Int,
    val criterionMet: Boolean,
    val createdAt: Long
)

/**
 * Blind therapist rating of one attempt (Story 5.7, Goal 8), written only by the Blind review screen through
 * [BlindReview]. One row per attempt (unique [attemptId], insert-or-replace). Ratings never feed levels, reports,
 * plans or compare (AD-9): only `correction` rows do.
 */
@Entity(
    tableName = "blind_rating",
    foreignKeys = [
        ForeignKey(entity = AttemptEntity::class, parentColumns = ["id"], childColumns = ["attemptId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["attemptId"], unique = true)]
)
data class BlindRatingEntity(@PrimaryKey val id: String, val attemptId: String, val verdict: AttemptVerdict, val createdAt: Long)
