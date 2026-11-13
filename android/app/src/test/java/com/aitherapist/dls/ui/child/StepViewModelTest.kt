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
import com.aitherapist.dls.data.ResumableSessions
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
import kotlinx.coroutines.flow.flowOf
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
        val sessions = linkedMapOf<String, SessionEntity>()
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
            sessions[session.id] = session
        }

        override suspend fun update(session: SessionEntity) {
            maybeFail()
            this.session = session
            sessions[session.id] = session
        }

        override suspend fun addAttempt(attempt: AttemptEntity) {
            maybeFail()
            rows += attempt
        }

        override suspend fun resumable(childId: String) = sessions.values.filter {
            it.childId == childId && (it.state == SessionState.RUNNING || it.state == SessionState.PAUSED)
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

    private val resumable =
        object : ResumableSessions {
            override fun observeResumable() = flowOf(store.sessions.values.toList())

            override suspend fun session(id: String) = store.sessions[id]
        }

    private fun vm(vararg steps: CatalogStep): StepViewModel = vmFor(null, *steps)

    /** Existing-flow tests tap Start at once; [tapStart] false keeps the task start screen. */
    private fun vmFor(sessionId: String?, vararg steps: CatalogStep, tapStart: Boolean = true, named: Boolean = true): StepViewModel {
        val catalog =
            object : CatalogRepository {
                override suspend fun load() = Result.success(
                    Catalog(
                        CatalogVersion("v1", false, steps.size, 0),
                        listOf(CatalogTask("t.x", "a", steps.toList(), nameAr = if (named) "task name" else "t.x", hasName = named)),
                        emptyList()
                    )
                )
            }
        val clock = Clock { scope.testScheduler.currentTime }
        val runners = AssessmentRunnerFactory(store, NoneVerifier, clock, dispatcher, newId = { "id${id++}" })
        return StepViewModel(catalog, children, { CharacterIndex(listOf(variant)) }, runners, resumable, audio, keys, clock, scope).also {
            it.start("c1", "t.x", sessionId)
            if (tapStart && sessionId == null) {
                scope.testScheduler.runCurrent() // Start only counts once the run exists
                it.beginTask()
            }
        }
    }

    private fun StepViewModel.walk(): StepUiState.Walkthrough = uiState.value as StepUiState.Walkthrough

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
    fun `last step done plays the walkthrough, then done and the calm end visual`() = scope.runTest {
        val vm = vm(step(1))
        listenThenAttempt(vm)
        vm.onEvent(StepEvent.Done)
        runCurrent()
        assertEquals(
            StepUiState.Walkthrough(0, 1, "sentence 1", StepVisual("clip1.webp", "still1.webp", true, 1f, true), null),
            vm.uiState.value
        )
        assertEquals("still resumable during the walkthrough", SessionState.RUNNING, store.session?.state)
        assertFalse(vm.completed.value)
        audio.finish()
        runCurrent()
        assertEquals(StepUiState.Finished("idle.webp"), vm.uiState.value)
        assertEquals(SessionState.DONE, store.session?.state)
        assertTrue(vm.completed.value)
    }

    @Test
    fun `new session opens on the task start screen and start shows the first step`() = scope.runTest {
        val vm = vmFor(null, step(1), step(2), tapStart = false)
        runCurrent()
        assertEquals(StepUiState.Start("task name", "still1.webp", "idle.webp"), vm.uiState.value)
        assertEquals("session row created before Start", SessionState.RUNNING, store.session?.state)
        assertTrue("no step audio on the start screen", audio.played.isEmpty())
        vm.onEvent(StepEvent.Done)
        vm.onEvent(StepEvent.Help)
        runCurrent()
        assertTrue(vm.uiState.value is StepUiState.Start)
        vm.beginTask()
        runCurrent()
        assertEquals(0, vm.step().stepIndex)
        assertEquals(1, vm.step().promptLevel)
        assertEquals(listOf("n1.mp3"), audio.played)
    }

    @Test
    fun `start before the run exists is ignored and a missing task name shows no name`() = scope.runTest {
        val vm = vmFor(null, step(1), tapStart = false, named = false)
        vm.beginTask()
        runCurrent()
        assertEquals(StepUiState.Start(null, "still1.webp", "idle.webp"), vm.uiState.value)
    }

    @Test
    fun `adult pause on the start screen dims, start is ignored, resume undims`() = scope.runTest {
        val vm = vmFor(null, step(1), tapStart = false)
        runCurrent()
        vm.pause()
        runCurrent()
        assertTrue(vm.uiState.value.isPaused)
        vm.beginTask()
        runCurrent()
        assertTrue(vm.uiState.value is StepUiState.Start)
        vm.resume()
        runCurrent()
        assertFalse(vm.uiState.value.isPaused)
        assertTrue(audio.played.isEmpty())
    }

    @Test
    fun `adult pause stops the walkthrough and resume continues from the current step`() = scope.runTest {
        val vm = vm(step(1), step(2))
        repeat(2) {
            listenThenAttempt(vm)
            vm.onEvent(StepEvent.Done)
            runCurrent()
            advanceTimeBy(DoneDebouncer.WINDOW_MS)
        }
        audio.finish() // walkthrough step 1 done
        runCurrent()
        assertEquals(1, vm.walk().stepIndex)
        vm.pause()
        runCurrent()
        assertTrue(vm.walk().paused)
        assertFalse("audio stopped", audio.playing)
        val played = audio.played.size
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(1, vm.walk().stepIndex)
        assertEquals(SessionState.RUNNING, store.session?.state)
        vm.resume()
        runCurrent()
        assertFalse(vm.walk().paused)
        assertEquals(1, vm.walk().stepIndex)
        assertEquals(played + 1, audio.played.size)
        assertEquals("n2.mp3", audio.played.last())
        audio.finish()
        runCurrent()
        assertEquals(SessionState.DONE, store.session?.state)
    }

    @Test
    fun `audio that never reports its end does not hang the walkthrough`() = scope.runTest {
        val vm = vm(step(1), step(2))
        repeat(2) {
            listenThenAttempt(vm)
            vm.onEvent(StepEvent.Done)
            runCurrent()
            advanceTimeBy(DoneDebouncer.WINDOW_MS)
        }
        assertEquals(0, vm.walk().stepIndex)
        advanceTimeBy(StepViewModel.WALK_STEP_CAP_MS)
        runCurrent()
        assertEquals(1, vm.walk().stepIndex)
        advanceTimeBy(StepViewModel.WALK_STEP_CAP_MS)
        runCurrent()
        assertEquals(SessionState.DONE, store.session?.state)
        assertEquals(StepUiState.Finished("idle.webp"), vm.uiState.value)
    }

    @Test
    fun `failed done save is retried without replaying the walkthrough`() = scope.runTest {
        val vm = vm(step(1))
        listenThenAttempt(vm)
        vm.onEvent(StepEvent.Done)
        runCurrent()
        val played = audio.played.size
        store.failNext = 1
        audio.finish()
        runCurrent()
        assertEquals(SessionState.RUNNING, store.session?.state)
        assertEquals(StepUiState.Waiting, vm.uiState.value)
        advanceTimeBy(StepViewModel.RETRY_MS)
        runCurrent()
        assertEquals(SessionState.DONE, store.session?.state)
        assertEquals(StepUiState.Finished("idle.webp"), vm.uiState.value)
        assertEquals("walkthrough not replayed", played, audio.played.size)
    }

    @Test
    fun `continue with nothing recorded opens the start screen, mid task opens the saved step`() = scope.runTest {
        vmFor(null, step(1), step(2), tapStart = false)
        runCurrent()
        val fresh = store.session!!.copy(state = SessionState.PAUSED)
        store.sessions[fresh.id] = fresh
        val again = vmFor(fresh.id, step(1), step(2), tapStart = false)
        runCurrent()
        assertTrue(again.uiState.value is StepUiState.Start)

        val first = vm(step(1), step(2))
        listenThenAttempt(first)
        first.onEvent(StepEvent.Done)
        runCurrent()
        val mid = store.session!!
        val before = audio.played.size
        val next = vmFor(mid.id, step(1), step(2), tapStart = false)
        runCurrent()
        assertEquals(1, next.step().stepIndex)
        assertEquals("n2.mp3", audio.played.last())
        assertTrue(audio.played.size > before)
    }

    @Test
    fun `paired key on the start screen means start`() = scope.runTest {
        val vm = vmFor(null, step(1), tapStart = false)
        runCurrent()
        keys.press()
        runCurrent()
        assertEquals(0, vm.step().stepIndex)
        assertTrue(store.rows.isEmpty())
    }

    @Test
    fun `walkthrough plays every step in order with its audio`() = scope.runTest {
        val vm = vm(step(1), step(2), step(3))
        repeat(3) {
            listenThenAttempt(vm)
            vm.onEvent(StepEvent.Done)
            runCurrent()
            advanceTimeBy(DoneDebouncer.WINDOW_MS)
        }
        val seen = mutableListOf<Int>()
        repeat(3) {
            seen += vm.walk().stepIndex
            assertEquals(3, vm.walk().stepCount)
            assertEquals("n${it + 1}.mp3", audio.played.last())
            assertEquals("clip${it + 1}.webp", vm.walk().visual?.clip)
            assertEquals(SessionState.RUNNING, store.session?.state)
            audio.finish()
            runCurrent()
        }
        assertEquals(listOf(0, 1, 2), seen)
        assertEquals(SessionState.DONE, store.session?.state)
        assertEquals(StepUiState.Finished("idle.webp"), vm.uiState.value)
    }

    @Test
    fun `walkthrough uses the still without a clip and the reaction character without media, 1_5 s without audio`() = scope.runTest {
        val vm = vm(step(1, audio = false, media = null), step(2, media = StepMedia(null, "still2.webp")))
        repeat(2) {
            runCurrent()
            advanceTimeBy(StepViewModel.MISSING_AUDIO_DELAY_MS)
            runCurrent()
            audio.finish()
            runCurrent()
            vm.onEvent(StepEvent.Done)
            runCurrent()
            advanceTimeBy(DoneDebouncer.WINDOW_MS)
        }
        runCurrent()
        val first = vm.walk()
        assertEquals(0, first.stepIndex)
        assertNull(first.visual)
        assertEquals("talk.webp", first.reactionClip)
        advanceTimeBy(StepViewModel.MISSING_AUDIO_DELAY_MS)
        runCurrent()
        val second = vm.walk()
        assertEquals(1, second.stepIndex)
        assertEquals(StepVisual(null, "still2.webp", animate = false, scale = 1f, large = true), second.visual)
        assertNull(second.reactionClip)
        audio.finish()
        runCurrent()
        assertEquals(SessionState.DONE, store.session?.state)
    }

    @Test
    fun `continue after the last step replays the walkthrough then finishes`() = scope.runTest {
        vm(step(1))
        runCurrent()
        val saved = store.session!!.copy(stepIndex = 1, state = SessionState.PAUSED)
        store.sessions[saved.id] = saved
        val next = vmFor(saved.id, step(1), tapStart = false)
        runCurrent()
        assertEquals(0, next.walk().stepIndex)
        audio.finish()
        runCurrent()
        assertEquals(SessionState.DONE, store.sessions[saved.id]?.state)
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
    fun `clearing mid run keeps the session paused for continue, after finish it stays done`() = scope.runTest {
        val vm = vm(step(1), step(2))
        runCurrent()
        ViewModelStore().apply { put("vm", vm) }.clear()
        runCurrent()
        assertEquals(SessionState.PAUSED, store.session?.state)

        val done = vm(step(1))
        listenThenAttempt(done)
        done.onEvent(StepEvent.Done)
        runCurrent()
        audio.finish() // walkthrough
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

    @Test
    fun `adult sheet pause dims with idle character, freezes the window and resume restarts the attempt`() = scope.runTest {
        val vm = vm(step(1), step(2))
        listenThenAttempt(vm)
        vm.openAdultSheet()
        assertTrue(vm.adultSheet.value)
        vm.pause()
        runCurrent()
        assertFalse(vm.adultSheet.value)
        assertTrue(vm.step().paused)
        assertEquals("idle.webp", vm.step().reactionClip)
        assertFalse(vm.step().doneEnabled)
        assertEquals(SessionState.PAUSED, store.session?.state)
        advanceTimeBy(60_000)
        runCurrent()
        vm.onEvent(StepEvent.Done)
        runCurrent()
        assertTrue("no row for a frozen window", store.rows.isEmpty())
        vm.openAdultSheet()
        vm.resume()
        runCurrent()
        assertFalse(vm.step().paused)
        assertEquals(SessionState.RUNNING, store.session?.state)
        assertEquals(listOf("n1.mp3", "n1.mp3"), audio.played)
        assertFalse("attempt restarts from listen", vm.step().doneEnabled)
    }

    @Test
    fun `exit pauses the session and dismiss closes the sheet`() = scope.runTest {
        val vm = vm(step(1), step(2))
        listenThenAttempt(vm)
        vm.openAdultSheet()
        vm.dismissAdultSheet()
        assertFalse(vm.adultSheet.value)
        vm.openAdultSheet()
        vm.exit()
        assertFalse(vm.adultSheet.value)
        assertEquals("saved paused before exit returns", SessionState.PAUSED, store.session?.state)
        // PIN screen covers the step screen, then is cancelled: still paused, nothing restarts.
        vm.setActive(false)
        runCurrent()
        vm.setActive(true)
        runCurrent()
        assertTrue(vm.step().paused)
        assertEquals(listOf("n1.mp3"), audio.played)
        ViewModelStore().apply { put("vm", vm) }.clear()
        runCurrent()
        assertEquals("leaving keeps it paused", SessionState.PAUSED, store.session?.state)
    }

    @Test
    fun `continue opens the saved step and prompt level with a fresh attempt`() = scope.runTest {
        val first = vm(step(1), step(2))
        listenThenAttempt(first)
        first.onEvent(StepEvent.Done)
        runCurrent()
        assertEquals(1, first.step().stepIndex)
        val saved = store.session!!
        // Process death: the row stays running; Continue starts a new ViewModel on the same session.
        val next = vmFor(saved.id, step(1), step(2))
        runCurrent()
        assertEquals(1, next.step().stepIndex)
        assertEquals(1, next.step().promptLevel)
        assertFalse(next.step().doneEnabled)
        assertEquals(saved.id, store.session!!.id)
        assertEquals(1, store.rows.size)
    }

    @Test
    fun `continue with a corrupt resume point abandons it and shows the calm end`() = scope.runTest {
        val first = vm(step(1))
        runCurrent()
        val saved = store.session!!.copy(stepIndex = 7)
        store.sessions[saved.id] = saved
        val next = vmFor(saved.id, step(1))
        runCurrent()
        assertEquals(StepUiState.Finished("idle.webp"), next.uiState.value)
        assertEquals(SessionState.ABANDONED, store.sessions[saved.id]?.state)
        assertNotNull(first)
    }

    @Test
    fun `continue after a catalogue update or of a missing task abandons it and shows the calm end`() = scope.runTest {
        vm(step(1))
        runCurrent()
        val saved = store.session!!
        store.sessions[saved.id] = saved.copy(catalogVersion = "old")
        val next = vmFor(saved.id, step(1))
        runCurrent()
        assertEquals(StepUiState.Finished("idle.webp"), next.uiState.value)
        assertEquals(SessionState.ABANDONED, store.sessions[saved.id]?.state)

        store.sessions[saved.id] = saved.copy(taskId = "gone")
        val other = vmFor(saved.id, step(1))
        runCurrent()
        assertEquals(StepUiState.Finished("idle.webp"), other.uiState.value)
        assertEquals(SessionState.ABANDONED, store.sessions[saved.id]?.state)
    }
}
