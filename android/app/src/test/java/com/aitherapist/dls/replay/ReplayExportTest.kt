package com.aitherapist.dls.replay

import com.aitherapist.dls.data.AttemptEntity
import com.aitherapist.dls.data.AttemptVerdict
import com.aitherapist.dls.data.SessionEntity
import com.aitherapist.dls.data.SessionKind
import com.aitherapist.dls.data.SessionState
import com.aitherapist.dls.data.VerifierKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ReplayExportTest {
    private val session =
        SessionEntity("s1", "eval", "t", SessionKind.REPLAY, SessionState.DONE, 0, 1, "cv1", "rv1", 0, 1, 1)

    private fun attempt(clip: String?, verdict: AttemptVerdict, verifier: VerifierKind, model: String? = null) = AttemptEntity(
        id = "a-$clip",
        sessionId = "s1",
        stepId = "step.a",
        number = 1,
        promptLevel = 1,
        helpPressed = false,
        verdict = verdict,
        reason = if (model != null) "model" else "check_passed",
        verifier = verifier,
        modelId = model,
        promptVersion = model?.let { "p1" },
        latencyMs = 42,
        framePaths = listOf("x.bin"),
        clipFile = clip,
        createdAt = 0,
        modelReason = "secret, free text"
    )

    @Test
    fun `csv has the listed columns, one row per clip attempt, and no model reason`() {
        val csv =
            ReplayExport.csv(
                session,
                listOf(
                    attempt("step.a__1__done__v01.mp4", AttemptVerdict.DONE, VerifierKind.POSE),
                    attempt("step.a__1__not_done__v01.mp4", AttemptVerdict.CANNOT_TELL, VerifierKind.CLOUD, "deepseek-flash"),
                    attempt(null, AttemptVerdict.DONE, VerifierKind.POSE)
                )
            )
        val lines = csv.trimEnd().lines()
        assertEquals(
            "clip_file,step,scripted_outcome,verdict,reason,verifier,model_id,prompt_version,latency_ms,catalog_version,rules_version",
            lines[0]
        )
        assertEquals(3, lines.size)
        assertEquals("step.a__1__done__v01.mp4,step.a,done,done,check_passed,pose,,,42,cv1,rv1", lines[1])
        assertEquals("step.a__1__not_done__v01.mp4,step.a,not_done,cannot_tell,model,cloud,deepseek-flash,p1,42,cv1,rv1", lines[2])
        assertFalse("secret" in csv)
        assertFalse("eval" in csv)
    }
}
