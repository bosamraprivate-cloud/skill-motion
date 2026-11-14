package com.aitherapist.dls.ui.therapist

import com.aitherapist.dls.data.ResultsExport
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ResultsExportViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private class FakeExport(private val fail: Boolean = false) : ResultsExport {
        val written = mutableListOf<String>()

        override suspend fun exportTo(uri: String) {
            if (fail) throw IOException("disk full")
            written += uri
        }
    }

    @Test
    fun `cancelled picker writes nothing`() {
        val export = FakeExport()
        val vm = ResultsExportViewModel(export, dispatcher)
        vm.onPicked(null)
        assertEquals(emptyList<String>(), export.written)
        assertEquals(ExportStatus.IDLE, vm.status.value)
    }

    @Test
    fun `picked document is written`() {
        val export = FakeExport()
        val vm = ResultsExportViewModel(export, dispatcher)
        vm.onPicked("content://doc/1")
        assertEquals(listOf("content://doc/1"), export.written)
        assertEquals(ExportStatus.DONE, vm.status.value)
    }

    @Test
    fun `write failure shows a calm message`() {
        val vm = ResultsExportViewModel(FakeExport(fail = true), dispatcher)
        vm.onPicked("content://doc/1")
        assertEquals(ExportStatus.FAILED, vm.status.value)
    }

    @Test
    fun `missing child codes name the count`() {
        val export =
            object : ResultsExport {
                override suspend fun exportTo(uri: String) = throw com.aitherapist.dls.data.MissingChildCodeException(2)
            }
        val vm = ResultsExportViewModel(export, dispatcher)
        vm.onPicked("content://x")
        assertEquals(ExportStatus.MISSING_CODES, vm.status.value)
        assertEquals(2, vm.brokenSessions)
    }
}
