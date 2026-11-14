package com.aitherapist.dls.ui.therapist

import com.aitherapist.dls.catalog.Catalog
import com.aitherapist.dls.catalog.CatalogRepository
import com.aitherapist.dls.catalog.CatalogStep
import com.aitherapist.dls.catalog.CatalogTask
import com.aitherapist.dls.catalog.CatalogVersion
import com.aitherapist.dls.catalog.Method
import com.aitherapist.dls.catalog.StepDetector
import com.aitherapist.dls.data.AttemptEntity
import com.aitherapist.dls.data.AttemptVerdict
import com.aitherapist.dls.data.CorrectionEntity
import com.aitherapist.dls.data.CorrectionStore
import com.aitherapist.dls.data.EvidenceFrames
import com.aitherapist.dls.data.RewardEntity
import com.aitherapist.dls.data.SessionEntity
import com.aitherapist.dls.data.SessionHistory
import com.aitherapist.dls.data.SessionKind
import com.aitherapist.dls.data.SessionState
import com.aitherapist.dls.data.TrainingResultEntity
import com.aitherapist.dls.data.VerifierKind
import com.aitherapist.dls.data.sessionLevels
import com.aitherapist.dls.scoring.StepLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionDetailViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun step(n: Int) =
        CatalogStep("t.0$n", "a", "t", n, "ar $n", "", "", emptyList(), "", StepDetector(Method.POSE), null, emptyMap())

    private val catalog =
        Catalog(
            CatalogVersion("v", true, 3, 0),
            listOf(CatalogTask("t", "a", listOf(step(1), step(2), step(3)), nameAr = "Task")),
            emptyList()
        )

    private fun session(state: SessionState = SessionState.DONE, child: String = "c") =
        SessionEntity("s", child, "t", SessionKind.ASSESSMENT, state, 0, 1, "v", "3", 0, 1, 1)

    private fun attempt(stepId: String, n: Int, verdict: AttemptVerdict, level: Int = n, frames: List<String> = emptyList()) =
        AttemptEntity("$stepId-$n", "s", stepId, n, level, false, verdict, "r", VerifierKind.POSE, null, null, 7, frames, null, n.toLong())

    /** In-memory facts shared by the history reader and the correction writer (upsert on stepId, like the DB). */
    private class Facts(var session: SessionEntity?, val attempts: List<AttemptEntity>) :
        SessionHistory,
        CorrectionStore {
        val corrections = mutableListOf<CorrectionEntity>()
        var failWrites = false

        override suspend fun assessmentSessions(childId: String) = listOfNotNull(session)

        override suspend fun session(id: String) = session?.takeIf { it.id == id }

        override suspend fun attempts(sessionId: String) = attempts

        override suspend fun corrections(sessionId: String) = corrections.toList()

        override suspend fun trainingResults(childId: String) = emptyList<TrainingResultEntity>()

        override suspend fun rewards(childId: String) = emptyList<RewardEntity>()

        override suspend fun addCorrection(correction: CorrectionEntity) {
            if (failWrites) error("disk")
            corrections.removeAll { it.sessionId == correction.sessionId && it.stepId == correction.stepId }
            corrections += correction
        }

        override suspend fun clearCorrection(sessionId: String, stepId: String) {
            corrections.removeAll { it.sessionId == sessionId && it.stepId == stepId }
        }
    }

    private class FakeCatalog(private val catalog: Catalog) : CatalogRepository {
        override suspend fun load(): Result<Catalog> = Result.success(catalog)
    }

    private class FakeFrames : EvidenceFrames {
        override suspend fun read(relativePath: String): ByteArray? = if (relativePath == "ok") byteArrayOf(1, 2) else error("bad tag")
    }

    private var now = 100L

    private fun vm(facts: Facts) = SessionDetailViewModel(facts, FakeCatalog(catalog), facts, FakeFrames(), dispatcher, {
        now++
    }, { "id$now" })

    private fun facts(state: SessionState = SessionState.DONE) = Facts(
        session(state),
        listOf(
            attempt("t.01", 1, AttemptVerdict.NOT_DONE, frames = listOf("ok", "missing")),
            attempt("t.01", 2, AttemptVerdict.DONE),
            attempt("t.02", 1, AttemptVerdict.DONE)
        )
    )

    private fun SessionDetailViewModel.detail() = (uiState.value as SessionDetailUiState.Content).detail

    @Test
    fun `detail lists every catalogue step with attempts and automatic levels`() = runTest {
        val vm = vm(facts()).apply { load("c", "s") }
        val d = vm.detail()
        assertEquals("Task", d.taskName)
        assertEquals(listOf("t.01", "t.02", "t.03"), d.steps.map { it.stepId })
        assertEquals(StepLevel.TWO, d.steps[0].automatic)
        assertEquals(2, d.steps[0].attempts.size)
        assertEquals(StepLevel.THREE, d.steps[1].automatic)
        assertNull(d.steps[2].automatic)
        assertTrue("finished session: untried step can be corrected", d.steps[2].canCorrect)
    }

    @Test
    fun `correct 2 to 3 shows corrected and matches the results levels`() = runTest {
        val f = facts()
        val vm = vm(f).apply { load("c", "s") }
        vm.correct("t.01", StepLevel.THREE)
        val s = vm.detail().steps[0]
        assertTrue(s.corrected)
        assertEquals(StepLevel.TWO, s.automatic)
        assertEquals(StepLevel.THREE, s.effective)
        assertEquals(StepLevel.THREE, sessionLevels(f.attempts, f.corrections)["t.01"])
    }

    @Test
    fun `second correction replaces the first, not scored stores null, remove restores`() = runTest {
        val f = facts()
        val vm = vm(f).apply { load("c", "s") }
        vm.correct("t.01", StepLevel.THREE)
        vm.correct("t.01", StepLevel.NOT_SCORED)
        assertEquals(1, f.corrections.size)
        assertNull(f.corrections.single().level)
        assertEquals(StepLevel.NOT_SCORED, vm.detail().steps[0].effective)
        vm.removeCorrection("t.01")
        assertTrue(f.corrections.isEmpty())
        assertFalse(vm.detail().steps[0].corrected)
        assertEquals(StepLevel.TWO, vm.detail().steps[0].effective)
    }

    @Test
    fun `open step of a paused session cannot be corrected`() = runTest {
        val f = facts(SessionState.PAUSED)
        val vm = vm(f).apply { load("c", "s") }
        assertEquals(HistoryState.PAUSED, vm.detail().state)
        assertFalse(vm.detail().steps[2].canCorrect)
        assertTrue(vm.detail().steps[0].canCorrect)
        vm.correct("t.03", StepLevel.ONE)
        assertTrue(f.corrections.isEmpty())
    }

    @Test
    fun `failed write keeps saved levels and flags it`() = runTest {
        val f = facts().apply { failWrites = true }
        val vm = vm(f).apply { load("c", "s") }
        vm.correct("t.01", StepLevel.ONE)
        val state = vm.uiState.value as SessionDetailUiState.Content
        assertTrue(state.writeFailed)
        assertFalse(state.detail.steps[0].corrected)
    }

    @Test
    fun `other child or missing session is empty`() = runTest {
        assertEquals(SessionDetailUiState.Empty, vm(facts()).apply { load("other", "s") }.uiState.value)
        assertEquals(SessionDetailUiState.Empty, vm(Facts(null, emptyList())).apply { load("c", "s") }.uiState.value)
    }

    @Test
    fun `frames decrypt in memory and failures read as null`() = runTest {
        val vm = vm(facts())
        assertArrayEquals(byteArrayOf(1, 2), vm.frame("ok"))
        assertNull(vm.frame("tampered"))
    }

    @Test
    fun `invalid stored correction is ignored and saved steps outside the catalogue are kept`() {
        val d =
            buildSessionDetail(
                session(),
                listOf(attempt("gone", 1, AttemptVerdict.DONE)),
                listOf(CorrectionEntity("k", "s", "t.01", 7, null, 1)),
                catalog
            )
        assertFalse(d.steps[0].corrected)
        assertEquals("gone", d.steps.last().stepId)
        assertNull(d.steps.last().number)
    }
}
