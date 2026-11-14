package com.aitherapist.dls.replay

import com.aitherapist.dls.catalog.Catalog
import com.aitherapist.dls.catalog.CatalogRepository
import com.aitherapist.dls.catalog.CatalogStep
import com.aitherapist.dls.catalog.CatalogTask
import com.aitherapist.dls.catalog.CatalogVersion
import com.aitherapist.dls.catalog.Method
import com.aitherapist.dls.catalog.Step
import com.aitherapist.dls.catalog.StepDetector
import com.aitherapist.dls.data.AttemptEntity
import com.aitherapist.dls.data.ClipFiles
import com.aitherapist.dls.data.ReplayStore
import com.aitherapist.dls.data.SessionEntity
import com.aitherapist.dls.data.SessionKind
import com.aitherapist.dls.data.SessionState
import com.aitherapist.dls.data.SessionStore
import com.aitherapist.dls.session.AssessmentRunnerFactory
import com.aitherapist.dls.session.Clock
import com.aitherapist.dls.vision.AttemptImage
import com.aitherapist.dls.vision.Frame
import com.aitherapist.dls.vision.LandmarkFrame
import com.aitherapist.dls.vision.Outcome
import com.aitherapist.dls.vision.Verdict
import com.aitherapist.dls.vision.VerdictReason
import com.aitherapist.dls.vision.Verifier
import com.aitherapist.dls.vision.VerifyContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayServiceTest {
    private val sessions = linkedMapOf<String, SessionEntity>()
    private val rows = mutableListOf<AttemptEntity>()
    private var evalCreated = 0
    private var version = "cv1"

    private val store =
        object : SessionStore, ReplayStore {
            override suspend fun add(session: SessionEntity) {
                sessions[session.id] = session
            }

            override suspend fun update(session: SessionEntity) {
                sessions[session.id] = session
            }

            override suspend fun addAttempt(attempt: AttemptEntity) {
                rows += attempt
            }

            override suspend fun attempts(sessionId: String, stepId: String) = rows.filter {
                it.sessionId == sessionId &&
                    it.stepId == stepId
            }

            override suspend fun resumable(childId: String) = emptyList<SessionEntity>()

            override suspend fun evalChildId(): String = "eval".also { evalCreated++ }

            override suspend fun latestReplaySession() = sessions.values.lastOrNull { it.kind == SessionKind.REPLAY }

            override suspend fun attempts(sessionId: String) = rows.filter { it.sessionId == sessionId }
        }

    private val dir = Files.createTempDirectory("clips").toFile()
    private val clips =
        object : ClipFiles {
            override fun save(name: String, input: InputStream): File = file(name).also { f -> input.use { f.writeBytes(it.readBytes()) } }

            override fun file(name: String) = File(dir, name)
        }

    private fun step(id: String, method: Method) =
        CatalogStep(id, "a", "t.x", 1, "", "", "", emptyList(), "", StepDetector(method), null, emptyMap())

    private val catalog =
        object : CatalogRepository {
            override suspend fun load() = Result.success(
                Catalog(
                    CatalogVersion(version, false, 2, 0),
                    listOf(CatalogTask("t.x", "a", listOf(step("t.x.01", Method.POSE), step("t.x.02", Method.CLOUD)))),
                    emptyList()
                )
            )
        }

    /** Done when the clip had frames with a pose, else not done. */
    private val verifier =
        object : Verifier {
            override suspend fun verify(step: Step, frames: List<Frame>, landmarks: List<LandmarkFrame>, ctx: VerifyContext) = Verdict(
                if (landmarks.any {
                        it.pose != null
                    }
                ) {
                    Outcome.DONE
                } else {
                    Outcome.NOT_DONE
                },
                VerdictReason.CHECK_PASSED,
                latencyMs = 1
            )
        }

    private class Image : AttemptImage {
        override val width = 1
        override val height = 1

        override fun release() = Unit
    }

    private var currentHasPose = false
    private val mirroredSeen = mutableListOf<Boolean>()

    /** A clip file whose content is "pose" yields pose landmarks, "bad" is undecodable, anything else has no pose. */
    private val source =
        ReplaySource(
            { f, mirrored ->
                mirroredSeen += mirrored
                val content = f.readText()
                currentHasPose = content == "pose"
                if (content == "bad") {
                    null
                } else {
                    object : DecodedClip {
                        override val durationMs = if (content == "long") ReplaySource.MAX_DURATION_MS + 1 else 200L

                        override fun frameAt(timeMs: Long): AttemptImage = if (content ==
                            "cancel"
                        ) {
                            throw CancellationException("stop")
                        } else {
                            Image()
                        }

                        override fun close() = Unit
                    }
                }
            },
            { _, _ ->
                object : VideoDetector {
                    override fun detect(image: AttemptImage, timestampMs: Long) =
                        LandmarkFrame(timestampMs, if (currentHasPose) emptyList() else null)

                    override fun close() = Unit
                }
            }
        )
    private var ids = 0

    private fun TestScope.service(): ReplayService {
        val factory =
            AssessmentRunnerFactory(
                store,
                verifier,
                Clock { 0L },
                StandardTestDispatcher(testScheduler),
                newId = { "id${ids++}" }
            )
        return ReplayService(catalog, store, clips, factory, source)
    }

    private fun input(name: String, content: String = "pose") = ClipInput(name) { ByteArrayInputStream(content.toByteArray()) }

    @Test
    fun `import scores valid clips in one replay session and skips bad names`() = runTest {
        val run =
            service().import(
                listOf(
                    input("t.x.01__1__done__v1.mp4"),
                    input("t.x.01__2__done__v2.mp4", "still"),
                    input("t.x.02__1__not_done__v1.mp4", "bad"),
                    input("foo.mp4"),
                    input("zz.01__1__done__v.mp4"),
                    input("t.x.01__1__maybe__v.mp4"),
                    ClipInput("t.x.01__3__done__v.mp4") { null }
                )
            )
        assertEquals(1, sessions.size)
        val session = sessions.values.single()
        assertEquals(SessionKind.REPLAY, session.kind)
        assertEquals("eval", session.childId)
        assertEquals(run.sessionId, session.id)
        assertEquals(3, rows.size)
        assertTrue(rows.all { it.clipFile != null })
        assertEquals(listOf(Agreement.AGREE, Agreement.DISAGREE, Agreement.NO_VERDICT), run.results.map { it.agreement })
        assertEquals(VerdictReason.NOT_JUDGEABLE, run.results[2].reason)
        assertEquals(
            listOf(ClipProblem.MALFORMED_NAME, ClipProblem.UNKNOWN_STEP, ClipProblem.BAD_OUTCOME, ClipProblem.COPY_FAILED),
            run.skipped.map { it.problem }
        )
        assertTrue(File(dir, "t.x.01__1__done__v1.mp4").isFile)
        assertEquals(run.results, service().latest().results)
    }

    @Test
    fun `rescore creates a new session under the current catalogue version`() = runTest {
        val s = service()
        s.import(listOf(input("t.x.01__1__done__v1.mp4"), input("t.x.01__2__done__v2.mp4")))
        File(dir, "t.x.01__2__done__v2.mp4").delete()
        version = "cv2"
        val run = s.rescore()
        assertEquals(2, sessions.size)
        assertEquals("cv2", sessions.values.last().catalogVersion)
        assertEquals(1, run.results.size)
        assertEquals(listOf(ClipProblem.MISSING_FILE), run.skipped.map { it.problem })
    }

    @Test
    fun `nothing to score gives no session`() = runTest {
        val s = service()
        assertNull(s.rescore().sessionId)
        assertNull(s.latest().sessionId)
        val run = s.import(listOf(input("foo.mp4")))
        assertNull(run.sessionId)
        assertTrue(sessions.isEmpty())
        assertEquals(0, evalCreated)
    }

    @Test
    fun `duplicate names in one import and clips over 90 s are skipped with a message`() = runTest {
        val run =
            service().import(
                listOf(
                    input("t.x.01__1__done__v1.mp4"),
                    input("t.x.01__1__done__v1.mp4", "still"),
                    input("t.x.01__2__done__v1.mp4", "long")
                )
            )
        assertEquals(1, run.results.size)
        assertEquals(Agreement.AGREE, run.results.single().agreement)
        assertEquals(listOf(ClipProblem.DUPLICATE, ClipProblem.TOO_LONG), run.skipped.map { it.problem })
        assertEquals("pose", File(dir, "t.x.01__1__done__v1.mp4").readText())
        assertEquals(SessionState.DONE, sessions.values.single().state)
    }

    @Test
    fun `mirrored setting reaches the decoder for import and rescore`() = runTest {
        val s = service()
        s.import(listOf(input("t.x.01__1__done__v1.mp4")), mirrored = true)
        assertTrue(mirroredSeen.contains(true))
        mirroredSeen.clear()
        s.rescore(mirrored = false)
        assertTrue(mirroredSeen.isNotEmpty() && mirroredSeen.none { it })
    }

    @Test
    fun `an interrupted run leaves the session abandoned, never running`() = runTest {
        val result = runCatching { service().import(listOf(input("t.x.01__1__done__v1.mp4", "cancel"))) }
        assertTrue(result.exceptionOrNull() is CancellationException)
        assertEquals(SessionState.ABANDONED, sessions.values.single().state)
    }
}
