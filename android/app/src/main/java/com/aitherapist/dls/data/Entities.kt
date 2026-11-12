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
    val promptLevel: Int
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
    val createdAt: Long
)
