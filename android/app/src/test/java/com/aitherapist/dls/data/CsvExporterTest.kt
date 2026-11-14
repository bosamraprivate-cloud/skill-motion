package com.aitherapist.dls.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvExporterTest {
    private val child = ChildEntity("child-1", "Youssef", "C-1001", 6, characterVariant = ChildCharacter.BOY, createdAt = 0)

    private fun session(id: String, startedAt: Long, kind: SessionKind = SessionKind.ASSESSMENT, state: SessionState = SessionState.DONE) =
        SessionEntity(id, child.id, "task-eat", kind, state, startedAt, null, "cat-1", "3", 0, 1, 1)

    private fun attempt(
        sessionId: String,
        stepId: String,
        number: Int,
        verdict: AttemptVerdict,
        prompt: Int = number,
        reason: String = "pose_ok",
        verifier: VerifierKind = VerifierKind.POSE,
        clip: String? = null,
        at: Long = number.toLong()
    ) = AttemptEntity(
        "$sessionId-$stepId-$number",
        sessionId,
        stepId,
        number,
        prompt,
        false,
        verdict,
        reason,
        verifier,
        if (verifier == VerifierKind.CLOUD) "deepseek-flash" else null,
        null,
        120,
        listOf("frames/$sessionId/x.bin"),
        clip,
        at,
        modelReason = "Youssef lifted the cup"
    )

    private val order = listOf("eat-01", "eat-02", "eat-03", "eat-04")

    private fun fixture(): List<ExportSession> {
        val s1 = session("s1", 100)
        val s2 = session("s2", 50, SessionKind.REPLAY)
        val s3 = session("s3", 200, state = SessionState.PAUSED)
        val s1Attempts =
            listOf(
                // Saved out of catalogue order: eat-02 first.
                attempt("s1", "eat-02", 1, AttemptVerdict.NOT_DONE, reason = "hand_far, wrist low", at = 1),
                attempt("s1", "eat-02", 2, AttemptVerdict.DONE, verifier = VerifierKind.CLOUD, reason = "model", at = 2),
                attempt("s1", "eat-01", 1, AttemptVerdict.DONE, at = 3),
                attempt("s1", "eat-03", 1, AttemptVerdict.CANNOT_TELL, prompt = 1, at = 4),
                attempt("s1", "eat-03", 2, AttemptVerdict.CANNOT_TELL, prompt = 1, reason = "-say \"hi\"\nthen -1", at = 5)
            )
        val s2Attempts = listOf(attempt("s2", "eat-01", 1, AttemptVerdict.DONE, clip = "eat-01_done_v1.mp4"))
        val s3Attempts = listOf(attempt("s3", "eat-01", 1, AttemptVerdict.NOT_DONE))
        return listOf(
            ExportSession(
                s1,
                child.code,
                s1Attempts,
                listOf(
                    CorrectionEntity("c1", "s1", "eat-02", 3, null, 1),
                    CorrectionEntity("c2", "s1", "eat-03", null, null, 2),
                    // A corrected step without attempts.
                    CorrectionEntity("c3", "s1", "eat-04", 2, null, 3)
                ),
                mapOf("s1-eat-01-1" to AttemptVerdict.DONE, "s1-eat-02-1" to AttemptVerdict.CANNOT_TELL)
            ),
            ExportSession(s2, child.code, s2Attempts, emptyList(), mapOf("s2-eat-01-1" to AttemptVerdict.NOT_DONE)),
            ExportSession(s3, child.code, s3Attempts, emptyList(), emptyMap())
        )
    }

    private fun export() = CsvExporter.format(fixture()) { if (it == "task-eat") order else emptyList() }

    @Test
    fun `header is exact`() {
        assertEquals(
            "sessionId,childCode,taskId,stepId,attemptId,attemptNumber,promptLevel,verdict,reason,verifier,modelId,latencyMs," +
                "level,correctedLevel,blindVerdict,clipFile",
            export().lineSequence().first()
        )
    }

    @Test
    fun `no child name, frames or model free text`() {
        val text = export()
        assertFalse(text.contains(child.name))
        assertFalse(text.contains("frames/"))
        assertFalse(text.contains("lifted"))
        assertTrue(text.contains("C-1001"))
    }

    @Test
    fun `rows ordered by session start, step order, attempt number`() {
        // Data lines start with a session id; the quoted multi-line reason continues on a "then" line.
        val ids = export().lines().drop(1).filter { it.startsWith("s") }.map { it.split(",")[4] }
        assertEquals(
            listOf("s2-eat-01-1", "s1-eat-01-1", "s1-eat-02-1", "s1-eat-02-2", "s1-eat-03-1", "s1-eat-03-2", "", "s3-eat-01-1"),
            ids
        )
    }

    @Test
    fun `levels, corrections, blind verdicts and quoting`() {
        val lines = export().lines()
        assertEquals("s2,C-1001,task-eat,eat-01,s2-eat-01-1,1,1,done,pose_ok,pose,,120,3,,not_done,eat-01_done_v1.mp4", lines[1])
        assertEquals("s1,C-1001,task-eat,eat-01,s1-eat-01-1,1,1,done,pose_ok,pose,,120,3,,done,", lines[2])
        assertEquals("s1,C-1001,task-eat,eat-02,s1-eat-02-1,1,1,not_done,\"hand_far, wrist low\",pose,,120,2,3,cannot_tell,", lines[3])
        assertEquals("s1,C-1001,task-eat,eat-02,s1-eat-02-2,2,2,done,model,cloud,deepseek-flash,120,2,3,,", lines[4])
        // Two cannot tell in a row: not scored; the null correction is the token not_scored.
        assertEquals("s1,C-1001,task-eat,eat-03,s1-eat-03-1,1,1,cannot_tell,pose_ok,pose,,120,not_scored,not_scored,,", lines[5])
        assertEquals("s1,C-1001,task-eat,eat-03,s1-eat-03-2,2,1,cannot_tell,\"'-say \"\"hi\"\"", lines[6])
        assertEquals("then -1\",pose,,120,not_scored,not_scored,,", lines[7])
        // Correction without attempts: one row, only correctedLevel.
        assertEquals("s1,C-1001,task-eat,eat-04,,,,,,,,,,2,,", lines[8])
        // Paused session, open step: level empty.
        assertEquals("s3,C-1001,task-eat,eat-01,s3-eat-01-1,1,1,not_done,pose_ok,pose,,120,,,,", lines[9])
    }

    @Test
    fun `LF line ends, no BOM, quotes doubled`() {
        val text = export()
        assertFalse(text.contains("\r"))
        assertFalse(text.startsWith("﻿"))
        assertTrue(text.endsWith("\n"))
        assertEquals("\"a \"\"b\"\"\"", CsvExporter.quote("a \"b\""))
        assertEquals("\"x\ny\"", CsvExporter.quote("x\ny"))
    }

    @Test
    fun `formula cells get a leading quote`() {
        assertEquals("'=SUM(A1)", CsvExporter.quote("=SUM(A1)"))
        assertEquals("'+1", CsvExporter.quote("+1"))
        assertEquals("'-1", CsvExporter.quote("-1"))
        assertEquals("'@x", CsvExporter.quote("@x"))
        assertEquals("pose_ok", CsvExporter.quote("pose_ok"))
    }

    @Test
    fun `attemptNumber never decreases within a step, in saved order`() {
        val s = session("s", 1)
        // Saved order 1, 2, 3 even when createdAt ties; the export keeps saved order (what Rule 1 read).
        val saved = (1..3).map { attempt("s", "eat-01", it, AttemptVerdict.NOT_DONE, at = 7) }
        val text = CsvExporter.format(listOf(ExportSession(s, "C-1", saved, emptyList(), emptyMap()))) { order }
        val numbers = text.lines().drop(1).filter { it.isNotEmpty() }.map { it.split(",")[5].toInt() }
        assertEquals(listOf(1, 2, 3), numbers)
        assertTrue(numbers.zipWithNext().all { (a, b) -> b >= a })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `missing child code fails`() {
        CsvExporter.format(listOf(ExportSession(session("s", 1), "", emptyList(), emptyList(), emptyMap()))) { order }
    }

    @Test
    fun `missing child codes are counted, never dropped`() {
        val sessions =
            listOf(
                ExportSession(session("a", 1), "", emptyList(), emptyList(), emptyMap()),
                ExportSession(session("b", 2), "C-1", emptyList(), emptyList(), emptyMap()),
                ExportSession(session("c", 3), " ", emptyList(), emptyList(), emptyMap())
            )
        assertEquals(2, CsvExporter.brokenSessions(sessions))
        val e = runCatching { CsvExporter.rows(sessions) { order }.toList() }.exceptionOrNull()
        assertEquals(2, (e as MissingChildCodeException).count)
    }

    @Test
    fun `population is replay of the eval child or a live assessment`() {
        assertEquals(Population.VOLUNTEER, population(SessionKind.REPLAY, evalChild = true))
        assertEquals(Population.LIVE, population(SessionKind.ASSESSMENT, evalChild = false))
        assertEquals(Population.OTHER, population(SessionKind.REPLAY, evalChild = false))
        assertEquals(Population.OTHER, population(SessionKind.ASSESSMENT, evalChild = true))
    }

    @Test
    fun `dashboard rows are the file rows`() {
        val s = session("s", 1)
        val saved = listOf(attempt("s", "eat-01", 1, AttemptVerdict.DONE, reason = "a, \"b\""))
        val export = listOf(ExportSession(s, "C-1", saved, emptyList(), emptyMap()))
        val text = CsvExporter.format(export) { order }
        val fromRows = CsvExporter.rows(export) { order }.map { CsvExporter.COLUMNS.zip(it).toMap() }.toList()
        assertEquals(com.aitherapist.dls.scoring.Metrics.parse(text).map { it.cells }, fromRows)
    }

    @Test
    fun `steps outside the catalogue come last`() {
        val s = session("s", 1)
        val rows = listOf(attempt("s", "zz", 1, AttemptVerdict.DONE, at = 1), attempt("s", "eat-01", 1, AttemptVerdict.DONE, at = 2))
        val text = CsvExporter.format(listOf(ExportSession(s, "C-1", rows, emptyList(), emptyMap()))) { order }
        assertEquals(listOf("eat-01", "zz"), text.lines().drop(1).filter { it.isNotEmpty() }.map { it.split(",")[3] })
    }

    /**
     * The committed sample that `tools/eval/test_compute_metrics.py` parses (round trip) must equal the export.
     * Regenerate with `./gradlew :app:testDebugUnitTest --tests "*CsvExporterTest" -DupdateFixtures=true`.
     */
    @Test
    fun `sample export fixture is current`() {
        val dir = File(checkNotNull(System.getProperty("evalFixturesDir")) { "evalFixturesDir not set by Gradle" })
        val file = File(dir, "results_export_sample.csv")
        if (System.getProperty("updateFixtures") == "true") file.writeText(export(), Charsets.UTF_8)
        assertTrue("missing $file", file.isFile)
        assertEquals(export(), file.readText(Charsets.UTF_8))
    }
}
