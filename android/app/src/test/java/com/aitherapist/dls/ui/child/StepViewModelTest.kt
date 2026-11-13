package com.aitherapist.dls.ui.child

import androidx.lifecycle.ViewModelStore
import com.aitherapist.dls.audio.FakeAudioPlayer
import com.aitherapist.dls.catalog.Catalog
import com.aitherapist.dls.catalog.CatalogRepository
import com.aitherapist.dls.catalog.CatalogStep
import com.aitherapist.dls.catalog.CatalogTask
import com.aitherapist.dls.catalog.CatalogVersion
import com.aitherapist.dls.catalog.CharacterIndex
import com.aitherapist.dls.catalog.CharacterVariant
import com.aitherapist.dls.catalog.Method
import com.aitherapist.dls.catalog.ReactionClip
import com.aitherapist.dls.catalog.StepAudio
import com.aitherapist.dls.catalog.StepDetector
import com.aitherapist.dls.catalog.StepMedia
import com.aitherapist.dls.data.AttemptEntity
import com.aitherapist.dls.data.ChildCharacter
import com.aitherapist.dls.data.SessionEntity
import com.aitherapist.dls.data.SessionKind
import com.aitherapist.dls.data.SessionState
import com.aitherapist.dls.data.SessionStore
import com.aitherapist.dls.session.AssessmentRunnerFactory
import com.aitherapist.dls.session.Clock
import com.aitherapist.dls.ui.therapist.FakeChildProfiles
import com.aitherapist.dls.vision.NoneVerifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StepViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)

    private class FakeStore : SessionStore {
        var session: SessionEntity? = null
        val rows = mutableListOf<AttemptEntity>()
        var failNext = 0

        private fun maybeFail() {
            if (failNext > 0) {
                failNext--
                error("disk full")
            }
        }

        override suspend fun add(session: SessionEntity) {
            maybeFail()
            this.session = session
        }

        override suspend fun update(session: SessionEntity) {
            maybeFail()
            this.session = session
        }

        override suspend fun addAttempt(attempt: AttemptEntity) {
            maybeFail()
            rows += attempt
        }

        override suspend fun attempts(sessionId: String, stepId: String) = rows.filter { it.stepId == stepId }
    }

    private val store = FakeStore()
    private var id = 0
    private val audio = FakeAudioPlayer()
    private val keys = ChildModeKeys()
    private val children = FakeChildProfiles(FakeChildProfiles.child("c1", "Ali", "C-001", character = ChildCharacter.BOY))
    private val variant =
        CharacterVariant(
            "boy",
            listOf(ReactionClip("idle", "idle.webp"), ReactionClip("talk", "talk.webp"), ReactionClip("point", "point.webp")),
            emptyList()
        )

    private fun step(n: Int, audio: Boolean = true, media: StepMedia? = StepMedia("clip$n.webp", "still$n.webp")) = CatalogStep(
        id = "t.x.0$n",
        area = "a",
        task = "t.x",
        number = n,
        textAr = "sentence $n",
        textEn = "s$n",
        subSkills = "",
        tags = emptyList(),
        source = "",
        detector = StepDetector(Method.NONE),
        audio = if (audio) StepAudio("n$n.mp3", "s$n.mp3") else null,
        media = mapOf("boy" to media)
    )

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm(vararg steps: CatalogStep): StepViewModel {
        val catalog =
            object : CatalogRepository {
                override suspend fun load() = Result.success(
                    Catalog(CatalogVersion("v1", false, steps.size, 0), listOf(CatalogTask("t.x", "a", steps.toList())), emptyList())
                )
            }
        val clock = Clock { scope.testScheduler.currentTime }
        val runners = AssessmentRunnerFactory(store, NoneVerifier, clock, dispatcher, newId = { "id${id++}" })
        return StepViewModel(catalog, children, { CharacterIndex(listOf(variant)) }, runners, audio, keys, clock, scope).also {
            it.start("c1", "t.x")
        }
    }

    private fun StepViewModel.step(): StepUiState.Step = uiState.value as StepUiState.Step

    private fun TestScope.listenThenAttempt(vm: StepViewModel) {
        runCurrent()
        audio.finish()
        runCurrent()
        assertTrue(vm.step().doneEnabled)
    }

    @Test
    fun `level 1 shows still, sentence and normal audio with talk, done dimmed until audio ends`() = scope.runTest {
        val vm = vm(step(1), step(2))
        runCurrent()
        val ui = vm.step()
        assertEquals("sentence 1", ui.sentence)
        assertEquals(StepVisual("clip1.webp", "still1.webp", animate = false, scale = 1f, large = false), ui.visual)
        assertEquals("talk.webp", ui.reactionClip)
        assertFalse(ui.doneEnabled)
        assertEquals(listOf("n1.mp3"), audio.played)
        assertEquals(SessionKind.ASSESSMENT, store.session?.kind)
        audio.finish()
        runCurrent()
        assertTrue(vm.step().doneEnabled)
        assertEquals("idle.webp", vm.step().reactionClip)
    }

    @Test
    fun `done while audio plays is ignored`() = scope.runTest {
        val vm = vm(step(1), step(2))
        runCurrent()
        vm.onEvent(StepEvent.Done)
        runCurrent()
        assertEquals(0, vm.step().stepIndex)
        assertTrue(store.rows.isEmpty())
        assertFalse(vm.step().doneEnabled)
    }

    @Test
    fun `help raises to level 2 with audio again, point and enlarged still`() = scope.runTest {
        val vm = vm(step(1), step(2))
        listenThenAttempt(vm)
        vm.onEvent(StepEvent.Help)
        runCurrent()
        val ui = vm.step()
        assertEquals(2, ui.promptLevel)
        assertEquals(listOf("n1.mp3", "n1.mp3"), audio.played)
        assertEquals("point.webp", ui.reactionClip)
        assertEquals(StepViewModel.LEVEL_2_SCALE, ui.visual?.scale)
        assertFalse(ui.doneEnabled)
    }

    @Test
    fun `level 3 plays the clip large with slow audio and hides the reaction character`() = scope.runTest {
        val vm = vm(step(1), step(2))
        runCurrent()
        vm.onEvent(StepEvent.Help)
        runCurrent()
        advanceTimeBy(DoneDebouncer.WINDOW_MS)
        vm.onEvent(StepEvent.Help)
        runCurrent()
        val ui = vm.step()
        assertEquals(3, ui.promptLevel)
        assertEquals("s1.mp3", audio.played.last())
        assertEquals(StepVisual("clip1.webp", "still1.webp", animate = true, scale = 1f, large = true), ui.visual)
        assertNull(ui.reactionClip)
    }

    @Test
    fun `level 3 without a clip shows the still large`() = scope.runTest {
        val vm = vm(step(1, media = StepMedia(null, "still1.webp")))
        runCurrent()
        repeat(2) {
            vm.onEvent(StepEvent.Help)
            runCurrent()
            advanceTimeBy(DoneDebouncer.WINDOW_MS)
        }
        assertEquals(StepVisual(null, "still1.webp", animate = false, scale = 1f, large = true), vm.step().visual)
    }

    @Test
    fun `missing audio shows the sentence and opens the window after 1_5 s`() = scope.runTest {
        val vm = vm(step(1, audio = false), step(2))
        runCurrent()
        assertTrue(audio.played.isEmpty())
        assertEquals("sentence 1", vm.step().sentence)
        advanceTimeBy(StepViewModel.MISSING_AUDIO_DELAY_MS - 1)
        runCurrent()
        assertFalse(vm.step().doneEnabled)
        advanceTimeBy(1)
        runCurrent()
        assertTrue(vm.step().doneEnabled)
    }

    @Test
    fun `audio file that cannot start falls back to the same delay`() = scope.runTest {
        audio.available = { false }
        val vm = vm(step(1), step(2))
        runCurrent()
        advanceTimeBy(StepViewModel.MISSING_AUDIO_DELAY_MS)
        runCurrent()
        assertTrue(vm.step().doneEnabled)
    }

    @Test
    fun `missing visual shows the reaction character only`() = scope.runTest {
        val vm = vm(step(1, media = null))
        runCurrent()
        assertNull(vm.step().visual)
        assertNotNull(vm.step().reactionClip)
        repeat(2) {
            vm.onEvent(StepEvent.Help)
            runCurrent()
            advanceTimeBy(DoneDebouncer.WINDOW_MS)
        }
        assertNotNull("no clip to show, so the reaction character stays", vm.step().reactionClip)
    }

    @Test
    fun `key press in attempt sends one done and a second within 1 s is ignored`() = scope.runTest {
        val vm = vm(step(1), step(2), step(3))
        listenThenAttempt(vm)
        keys.press()
        runCurrent()
        assertEquals(1, store.rows.size)
        assertEquals(1, vm.step().stepIndex)
        audio.finish()
        runCurrent()
        assertTrue(vm.step().doneEnabled)
        advanceTimeBy(500)
        keys.press()
        runCurrent()
        assertEquals(1, store.rows.size)
        assertEquals(1, vm.step().stepIndex)
    }

    @Test
    fun `repeat replays audio and talk without changing the prompt`() = scope.runTest {
        val vm = vm(step(1), step(2))
        runCurrent()
        vm.onEvent(StepEvent.Repeat)
        runCurrent()
        assertEquals(listOf("n1.mp3", "n1.mp3"), audio.played)
        assertEquals(1, vm.step().promptLevel)
        assertEquals("talk.webp", vm.step().reactionClip)
        audio.finish()
        runCurrent()
        advanceTimeBy(DoneDebouncer.WINDOW_MS)
        vm.onEvent(StepEvent.Repeat)
        runCurrent()
        assertEquals(3, audio.played.size)
        assertEquals(1, vm.step().promptLevel)
        assertFalse(vm.step().doneEnabled)
        assertEquals("talk.webp", vm.step().reactionClip)
    }

    @Test
    fun `save failure shows the calm wait and retries automatically`() = scope.runTest {
        val vm = vm(step(1), step(2))
        runCurrent()
        store.failNext = 1
        audio.finish()
        runCurrent()
        assertEquals(StepUiState.Waiting, vm.uiState.value)
        advanceTimeBy(StepViewModel.RETRY_MS)
        runCurrent()
        assertTrue(vm.step().doneEnabled)
    }

    @Test
    fun `last step done finishes with the calm end visual`() = scope.runTest {
        val vm = vm(step(1))
        listenThenAttempt(vm)
        vm.onEvent(StepEvent.Done)
        runCurrent()
        assertEquals(StepUiState.Finished("idle.webp"), vm.uiState.value)
        assertEquals(SessionState.DONE, store.session?.state)
    }

    @Test
    fun `an open window with no done closes by itself through ticks`() = scope.runTest {
        val vm = vm(step(1), step(2))
        listenThenAttempt(vm)
        advanceTimeBy(46_000)
        runCurrent()
        assertEquals(1, vm.step().stepIndex)
    }

    @Test
    fun `unknown child keeps the calm wait`() = scope.runTest {
        children.rows.value = emptyList()
        val vm = vm(step(1))
        runCurrent()
        assertEquals(StepUiState.Waiting, vm.uiState.value)
    }

    @Test
    fun `double tap on help raises one level only`() = scope.runTest {
        val vm = vm(step(1), step(2))
        runCurrent()
        vm.onEvent(StepEvent.Help)
        vm.onEvent(StepEvent.Help)
        runCurrent()
        assertEquals(2, vm.step().promptLevel)
        advanceTimeBy(DoneDebouncer.WINDOW_MS)
        vm.onEvent(StepEvent.Help)
        runCurrent()
        assertEquals(3, vm.step().promptLevel)
    }

    @Test
    fun `inactive screen stops audio and ticks, return restarts the prompt with a fresh window`() = scope.runTest {
        val vm = vm(step(1), step(2))
        listenThenAttempt(vm)
        vm.setActive(false)
        runCurrent()
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals("no window closed while covered", 0, vm.step().stepIndex)
        assertTrue(store.rows.isEmpty())
        vm.setActive(true)
        runCurrent()
        assertEquals(listOf("n1.mp3", "n1.mp3"), audio.played)
        assertFalse(vm.step().doneEnabled)
        assertEquals(1, vm.step().promptLevel)
        vm.setActive(false)
        runCurrent()
        assertFalse("audio stopped while covered", audio.playing)
    }

    @Test
    fun `clearing mid run marks the session abandoned, after finish it stays done`() = scope.runTest {
        val vm = vm(step(1), step(2))
        runCurrent()
        ViewModelStore().apply { put("vm", vm) }.clear()
        runCurrent()
        assertEquals(SessionState.ABANDONED, store.session?.state)

        val done = vm(step(1))
        listenThenAttempt(done)
        done.onEvent(StepEvent.Done)
        runCurrent()
        ViewModelStore().apply { put("vm2", done) }.clear()
        runCurrent()
        assertEquals(SessionState.DONE, store.session?.state)
    }

    @Test
    fun `two runs make two sessions`() = scope.runTest {
        val ids = mutableSetOf<String>()
        vm(step(1))
        runCurrent()
        ids += store.session!!.id
        vm(step(1))
        runCurrent()
        ids += store.session!!.id
        assertEquals(2, ids.size)
    }
}
