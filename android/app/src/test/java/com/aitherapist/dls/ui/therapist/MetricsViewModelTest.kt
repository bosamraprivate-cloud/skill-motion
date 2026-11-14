package com.aitherapist.dls.ui.therapist

import com.aitherapist.dls.data.MetricsRows
import com.aitherapist.dls.data.MissingChildCodeException
import com.aitherapist.dls.data.ResultsRead
import com.aitherapist.dls.scoring.Metrics
import java.io.IOException
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
class MetricsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val head = "sessionId,stepId,attemptId,attemptNumber,promptLevel,verdict,level,correctedLevel,blindVerdict,clipFile\n"

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private class Fake(val volunteerCsv: String, val liveCsv: String, var fail: Boolean = false, var missing: Int = 0) : ResultsRead {
        val asked = mutableListOf<Boolean>()

        override suspend fun metricsRows(volunteer: Boolean): MetricsRows {
            asked += volunteer
            if (fail) throw IOException("x")
            if (missing > 0) throw MissingChildCodeException(missing)
            return MetricsRows(Metrics.parse(if (volunteer) volunteerCsv else liveCsv).map { it.cells }, 2)
        }
    }

    @Test
    fun filterRecomputesAndEmptyAndError() {
        val fake = Fake(head + "s,a,a1,1,1,done,3,,done,\n", head)
        val vm = MetricsViewModel(fake, dispatcher)
        val content = vm.uiState.value as MetricsUiState.Content
        assertTrue(content.volunteer)
        assertEquals(1.0, content.metrics.agreement!!, 0.0)
        assertEquals(2, content.excluded)
        vm.select(true)
        assertEquals(listOf(true), fake.asked)
        vm.select(false)
        assertEquals(MetricsUiState.Empty(false), vm.uiState.value)
        fake.fail = true
        vm.retry()
        assertEquals(MetricsUiState.Error(false, "IOException"), vm.uiState.value)
        fake.fail = false
        fake.missing = 3
        vm.retry()
        assertEquals(MetricsUiState.MissingCodes(false, 3), vm.uiState.value)
    }
}
