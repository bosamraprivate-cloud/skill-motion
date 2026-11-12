package com.aitherapist.dls.ui.sample

import app.cash.turbine.test
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SampleViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Emits the scripted result of each call in turn; a `null` entry throws. */
    private class ScriptedRepository(vararg results: List<String>?) : SampleRepository {
        private val queue = ArrayDeque(results.toList())

        override fun items(): Flow<List<String>> {
            val next = queue.removeFirst()
            return flow {
                delay(FAKE_LATENCY_MS)
                if (next == null) throw IOException("boom")
                emit(next)
            }
        }

        companion object {
            const val FAKE_LATENCY_MS = 10L
        }
    }

    @Test
    fun `loading then content when repository emits items`() = runTest {
        val vm = SampleViewModel(ScriptedRepository(listOf("a", "b")))
        vm.uiState.test {
            assertEquals(SampleUiState.Loading, awaitItem())
            assertEquals(SampleUiState.Content(listOf("a", "b")), awaitItem())
        }
    }

    @Test
    fun `empty when repository emits empty list`() = runTest {
        val vm = SampleViewModel(ScriptedRepository(emptyList()))
        vm.uiState.test {
            assertEquals(SampleUiState.Loading, awaitItem())
            assertEquals(SampleUiState.Empty, awaitItem())
        }
    }

    @Test
    fun `error with reason when repository throws and never crashes`() = runTest {
        val vm = SampleViewModel(ScriptedRepository(null))
        vm.uiState.test {
            assertEquals(SampleUiState.Loading, awaitItem())
            assertEquals(SampleUiState.Error("IOException"), awaitItem())
        }
    }

    @Test
    fun `retry re-subscribes and recovers`() = runTest {
        val vm = SampleViewModel(ScriptedRepository(null, listOf("x")))
        vm.uiState.test {
            assertEquals(SampleUiState.Loading, awaitItem())
            assertEquals(SampleUiState.Error("IOException"), awaitItem())
            vm.onEvent(SampleEvent.Retry)
            assertEquals(SampleUiState.Loading, awaitItem())
            assertEquals(SampleUiState.Content(listOf("x")), awaitItem())
        }
    }
}
