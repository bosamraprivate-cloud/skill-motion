package com.aitherapist.dls.ui.therapist

import com.aitherapist.dls.catalog.Catalog
import com.aitherapist.dls.catalog.CatalogRepository
import com.aitherapist.dls.catalog.CatalogStep
import com.aitherapist.dls.catalog.CatalogTask
import com.aitherapist.dls.catalog.CatalogVersion
import com.aitherapist.dls.catalog.Method
import com.aitherapist.dls.catalog.StepDetector
import com.aitherapist.dls.data.AttemptVerdict
import com.aitherapist.dls.data.BlindItem
import com.aitherapist.dls.data.BlindRatingEntity
import com.aitherapist.dls.data.BlindReview
import com.aitherapist.dls.data.EvidenceFrames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BlindReviewViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun step(n: Int) =
        CatalogStep("t.0$n", "a", "t", n, "ar $n", "", "", emptyList(), "", StepDetector(Method.POSE), null, emptyMap())

    private val catalog =
        object : CatalogRepository {
            override suspend fun load() = Result.success(
                Catalog(CatalogVersion("v", true, 2, 0), listOf(CatalogTask("t", "a", listOf(step(1), step(2)))), emptyList())
            )
        }

    private val frames = object : EvidenceFrames {
        override suspend fun read(relativePath: String): ByteArray? = null
    }

    private class Fake(val items: List<BlindItem>, rated: Map<String, AttemptVerdict> = emptyMap()) : BlindReview {
        val rows = rated.toMutableMap()
        var fail = false

        override suspend fun candidates() = items

        override suspend fun ratedAttemptIds() = rows.keys.toSet()

        var exists = true

        override suspend fun attemptExists(attemptId: String) = exists

        override suspend fun rate(rating: BlindRatingEntity) {
            check(!fail) { "write failed" }
            rows[rating.attemptId] = rating.verdict
        }
    }

    private fun fields(type: Class<*>) =
        type.declaredFields.filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }.map { it.name }.toSet()

    private fun item(id: String, step: String) = BlindItem(id, step, listOf("frames/s/$id-0.bin"))

    private fun vm(review: BlindReview) = BlindReviewViewModel(review, catalog, frames, dispatcher, { 5L }, { "id" }, 42L)

    @Test
    fun `opens on an unrated attempt with frames and step sentence only`() {
        val review = Fake(listOf(item("a1", "t.01"), item("a2", "t.02")), mapOf("a1" to AttemptVerdict.DONE))
        val state = vm(review).uiState.value as BlindReviewUiState.Content
        assertEquals(BlindAttempt("a2", "ar 2", listOf("frames/s/a2-0.bin")), state.attempt)
        assertEquals(1, state.rated)
        assertEquals(2, state.total)
    }

    @Test
    fun `nothing shown can carry the verdict, reason, level, verifier or child`() {
        val shown = fields(BlindAttempt::class.java)
        assertEquals(setOf("attemptId", "stepText", "framePaths", "clip"), shown)
        val item = fields(BlindItem::class.java)
        assertEquals(setOf("attemptId", "stepId", "framePaths", "clip"), item)
    }

    @Test
    fun `rating saves one row and moves on until all rated`() {
        val review = Fake(listOf(item("a1", "t.01"), item("a2", "t.02")))
        val vm = vm(review)
        val first = (vm.uiState.value as BlindReviewUiState.Content).attempt.attemptId
        vm.rate(BlindAnswer.NOT_DONE)
        assertEquals(AttemptVerdict.NOT_DONE, review.rows[first])
        val second = vm.uiState.value as BlindReviewUiState.Content
        assertEquals(1, second.rated)
        assertTrue(second.attempt.attemptId != first)
        vm.rate(BlindAnswer.CANNOT_TELL)
        assertEquals(BlindReviewUiState.AllRated(2), vm.uiState.value)
    }

    @Test
    fun `skip leaves the attempt unrated and brings it back later`() {
        val review = Fake(listOf(item("a1", "t.01"), item("a2", "t.02")))
        val vm = vm(review)
        val first = (vm.uiState.value as BlindReviewUiState.Content).attempt.attemptId
        vm.skip()
        val next = vm.uiState.value as BlindReviewUiState.Content
        assertTrue(next.attempt.attemptId != first)
        assertTrue(review.rows.isEmpty())
        vm.rate(BlindAnswer.DONE)
        assertEquals(first, (vm.uiState.value as BlindReviewUiState.Content).attempt.attemptId)
    }

    @Test
    fun `failed write keeps the attempt and says so`() {
        val review = Fake(listOf(item("a1", "t.01"))).apply { fail = true }
        val vm = vm(review)
        vm.rate(BlindAnswer.DONE)
        val state = vm.uiState.value as BlindReviewUiState.Content
        assertTrue(state.writeFailed)
        assertEquals(0, state.rated)
    }

    @Test
    fun `failed write of a deleted attempt drops it and recounts`() {
        val review = Fake(listOf(item("a1", "t.01"), item("a2", "t.02"))).apply { fail = true }
        val vm = vm(review)
        review.exists = false
        vm.rate(BlindAnswer.DONE)
        val state = vm.uiState.value as BlindReviewUiState.Content
        assertEquals(1, state.total)
        assertEquals(false, state.writeFailed)
    }

    @Test
    fun `order does not depend on how many attempts a step has`() {
        // Step "big" has 3 attempts, nine other steps have 1 each; over many runs "big" sits on average mid-queue.
        val items = (1..3).map { item("b$it", "big") } + (1..9).map { item("s$it", "small$it") }
        val runs = 400
        val meanBig = (1..runs).sumOf { seed ->
            blindOrder(items, seed.toLong()).withIndex().filter { it.value.stepId == "big" }.sumOf { it.index }
        }.toDouble() / (runs * 3)
        assertEquals((items.size - 1) / 2.0, meanBig, 0.6)
    }

    @Test
    fun `no attempts with frames is the empty state and all rated is calm`() {
        assertEquals(BlindReviewUiState.Empty, vm(Fake(emptyList())).uiState.value)
        assertEquals(BlindReviewUiState.AllRated(1), vm(Fake(listOf(item("a", "t.01")), mapOf("a" to AttemptVerdict.DONE))).uiState.value)
    }

    @Test
    fun `order is deterministic per seed and spreads attempts of one step`() {
        val items = (1..4).map { item("x$it", "t.01") } + (1..4).map { item("y$it", "t.02") } + listOf(item("z", "t.03"))
        val order = blindOrder(items, 7L)
        assertEquals(order, blindOrder(items, 7L))
        assertEquals(items.toSet(), order.toSet())
        order.zipWithNext().forEach { (a, b) -> assertTrue("$a then $b", a.stepId != b.stepId) }
    }
}
