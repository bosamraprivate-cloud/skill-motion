package com.aitherapist.dls.replay

import com.aitherapist.dls.catalog.CatalogRepository
import com.aitherapist.dls.catalog.CatalogStep
import com.aitherapist.dls.data.AttemptEntity
import com.aitherapist.dls.data.AttemptVerdict
import com.aitherapist.dls.data.ClipFiles
import com.aitherapist.dls.data.ReplayStore
import com.aitherapist.dls.session.AssessmentRunner
import com.aitherapist.dls.session.AssessmentRunnerFactory
import java.io.InputStream
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** One picked file: its display name and a way to open it (null when it cannot be opened). */
class ClipInput(val fileName: String, val open: () -> InputStream?)

/** How the app's verdict compares with the scripted outcome. Cannot tell is doubt, never a disagreement. */
enum class Agreement { AGREE, DISAGREE, NO_VERDICT }

/** One scored clip as the results list shows it. */
data class ClipResult(
    val clipFile: String,
    val stepId: String,
    val scripted: ScriptedOutcome,
    val verdict: AttemptVerdict,
    val reason: String,
    val agreement: Agreement
)

/** A skipped clip and why. */
data class SkippedClip(val fileName: String, val problem: ClipProblem)

/** One replay run (or the latest stored one): its session id (null when nothing was scored), results and skips. */
data class ReplayRun(val sessionId: String?, val catalogVersion: String?, val results: List<ClipResult>, val skipped: List<SkippedClip>)

/**
 * Replay use case (Story 3.7). Every import, and every Re-score, is one new `replay` session of the hidden eval child,
 * scored through [AssessmentRunnerFactory.createReplay] (the live router, gate, verifiers and evidence path) under the
 * current catalogue, rules and model versions. Clips are copied to app-private storage first so Re-score works offline.
 * Never logs clip contents or names.
 */
class ReplayService(
    private val catalog: CatalogRepository,
    private val store: ReplayStore,
    private val clips: ClipFiles,
    private val runners: AssessmentRunnerFactory,
    private val source: ReplaySource
) {
    /** Validates, copies and scores [inputs] in one new replay session. */
    suspend fun import(inputs: List<ClipInput>, mirrored: Boolean = false): ReplayRun {
        val steps = catalogSteps()
        val skipped = mutableListOf<SkippedClip>()
        val valid = mutableListOf<ClipName>()
        val seen = mutableSetOf<String>()
        for (input in inputs) {
            if (!seen.add(input.fileName)) {
                skipped += SkippedClip(input.fileName, ClipProblem.DUPLICATE)
                continue
            }
            val clip = validate(input.fileName, steps, skipped) ?: continue
            val copied =
                try {
                    input.open()?.use { clips.save(clip.fileName, it) } != null
                } catch (e: CancellationException) {
                    throw e
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Exception
                ) {
                    false
                }
            when {
                !copied -> skipped += SkippedClip(input.fileName, ClipProblem.COPY_FAILED)
                tooLong(clip.fileName) -> skipped += SkippedClip(input.fileName, ClipProblem.TOO_LONG)
                else -> valid += clip
            }
        }
        return score(valid, steps, skipped, mirrored)
    }

    /** Scores the clips of the latest replay session again in a new session under the current versions. */
    suspend fun rescore(mirrored: Boolean = false): ReplayRun {
        val steps = catalogSteps()
        val latest = store.latestReplaySession() ?: return ReplayRun(null, null, emptyList(), emptyList())
        val names = store.attempts(latest.id).mapNotNull { it.clipFile }.distinct()
        val skipped = mutableListOf<SkippedClip>()
        val valid =
            names.mapNotNull { name ->
                validate(name, steps, skipped)?.takeIf {
                    when {
                        !clips.file(name).isFile -> false.also { skipped += SkippedClip(name, ClipProblem.MISSING_FILE) }
                        tooLong(name) -> false.also { skipped += SkippedClip(name, ClipProblem.TOO_LONG) }
                        else -> true
                    }
                }
            }
        return score(valid, steps, skipped, mirrored)
    }

    /** The latest replay session's results (empty when there is none). */
    suspend fun latest(): ReplayRun {
        val session = store.latestReplaySession() ?: return ReplayRun(null, null, emptyList(), emptyList())
        return ReplayRun(session.id, session.catalogVersion, store.attempts(session.id).mapNotNull(::result), emptyList())
    }

    private fun tooLong(name: String): Boolean = (source.durationMs(clips.file(name)) ?: 0L) > ReplaySource.MAX_DURATION_MS

    private suspend fun catalogSteps(): Pair<String, Map<String, CatalogStep>> {
        val loaded = catalog.load().getOrThrow()
        return loaded.catalogVersion to loaded.steps.associateBy { it.id }
    }

    private fun validate(fileName: String, steps: Pair<String, Map<String, CatalogStep>>, skipped: MutableList<SkippedClip>): ClipName? =
        when (val parsed = ClipName.parse(fileName)) {
            is ClipName.Parsed.Invalid -> null.also { skipped += SkippedClip(fileName, parsed.problem) }
            is ClipName.Parsed.Ok ->
                parsed.clip.takeIf { it.stepId in steps.second }
                    ?: null.also { skipped += SkippedClip(fileName, ClipProblem.UNKNOWN_STEP) }
        }

    private suspend fun score(
        valid: List<ClipName>,
        steps: Pair<String, Map<String, CatalogStep>>,
        skipped: List<SkippedClip>,
        mirrored: Boolean
    ): ReplayRun {
        if (valid.isEmpty()) return ReplayRun(null, null, emptyList(), skipped)
        val (version, byId) = steps
        val used = valid.map { it.stepId }.distinct()
        val catalogSteps = used.map { byId.getValue(it) }
        val runner =
            runners.createReplay(
                childId = store.evalChildId(),
                taskId = catalogSteps.map { it.task }.distinct().joinToString(TASK_SEPARATOR),
                catalogVersion = version,
                steps = catalogSteps.map { it.toStep() }
            )
        runner.startReplay()
        val results = mutableListOf<ClipResult>()
        val failed = skipped.toMutableList()
        var finished = false
        // The session always ends: done when every clip was tried, else abandoned (cancellation or a store failure).
        try {
            scoreAll(runner, valid, used, catalogSteps, mirrored, results, failed)
            runner.finishReplay()
            finished = true
        } finally {
            if (!finished) withContext(NonCancellable) { runner.abandon() }
        }
        return ReplayRun(runner.id, version, results, failed)
    }

    private suspend fun scoreAll(
        runner: AssessmentRunner,
        valid: List<ClipName>,
        used: List<String>,
        catalogSteps: List<CatalogStep>,
        mirrored: Boolean,
        results: MutableList<ClipResult>,
        failed: MutableList<SkippedClip>
    ) {
        for (clip in valid) {
            val index = used.indexOf(clip.stepId)
            val landmarkers = catalogSteps[index].detector.landmarkers.toSet()
            val row =
                try {
                    runner.scoreClip(index, clip.attemptNumber, clip.promptLevel, clip.fileName) { sink ->
                        source.read(clips.file(clip.fileName), landmarkers, mirrored, sink)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Exception
                ) {
                    null
                }
            if (row == null) failed += SkippedClip(clip.fileName, ClipProblem.SAVE_FAILED) else result(row)?.let(results::add)
        }
    }

    private companion object {
        const val TASK_SEPARATOR = ","

        fun result(row: AttemptEntity): ClipResult? {
            val clip = (ClipName.parse(row.clipFile ?: return null) as? ClipName.Parsed.Ok)?.clip ?: return null
            val agreement =
                when (row.verdict) {
                    AttemptVerdict.CANNOT_TELL -> Agreement.NO_VERDICT
                    AttemptVerdict.DONE -> if (clip.outcome == ScriptedOutcome.DONE) Agreement.AGREE else Agreement.DISAGREE
                    AttemptVerdict.NOT_DONE -> if (clip.outcome == ScriptedOutcome.NOT_DONE) Agreement.AGREE else Agreement.DISAGREE
                }
            return ClipResult(clip.fileName, row.stepId, clip.outcome, row.verdict, row.reason, agreement)
        }
    }
}
