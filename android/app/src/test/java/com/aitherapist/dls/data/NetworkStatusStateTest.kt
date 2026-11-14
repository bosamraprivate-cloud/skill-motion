package com.aitherapist.dls.data

import com.aitherapist.dls.ui.therapist.NetworkBanner
import com.aitherapist.dls.ui.therapist.networkBanner
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkStatusStateTest {
    private fun banner(s: NetworkStatusState) = networkBanner(s.online.value, s.proxyFailed.value)

    private fun TestScope.status() = NetworkStatusState(backgroundScope)

    @Test
    fun `offline shows the banner and coming back hides it`() = runTest {
        val s = status()
        assertEquals(NetworkBanner.NONE, banner(s))
        s.setOnline(false)
        assertEquals(NetworkBanner.OFFLINE, banner(s))
        s.setOnline(true)
        assertEquals(NetworkBanner.NONE, banner(s))
    }

    @Test
    fun `a failed proxy call shows the banner until a success`() = runTest {
        val s = status()
        s.reportProxy(s.beginProxyCall(), failed = true)
        assertEquals(NetworkBanner.PROXY_FAILED, banner(s))
        s.reportProxy(s.beginProxyCall(), failed = false)
        assertEquals(NetworkBanner.NONE, banner(s))
    }

    @Test
    fun `offline wins and reconnecting clears a failed call`() = runTest {
        val s = status()
        s.reportProxy(s.beginProxyCall(), failed = true)
        s.setOnline(false)
        assertEquals(NetworkBanner.OFFLINE, banner(s))
        assertTrue(s.proxyFailed.value)
        s.setOnline(true)
        assertFalse(s.proxyFailed.value)
        assertEquals(NetworkBanner.NONE, banner(s))
    }

    @Test
    fun `a failure from a call started before reconnecting is ignored`() = runTest {
        val s = status()
        s.setOnline(false)
        val started = s.beginProxyCall()
        s.setOnline(true)
        s.reportProxy(started, failed = true)
        assertFalse(s.proxyFailed.value)
        s.reportProxy(s.beginProxyCall(), failed = true)
        assertTrue(s.proxyFailed.value)
    }

    @Test
    fun `a failure expires after two minutes without new failures`() = runTest {
        val s = status()
        s.reportProxy(s.beginProxyCall(), failed = true)
        advanceTimeBy(NetworkStatus.FAILURE_EXPIRY_MS - 1_000)
        s.reportProxy(s.beginProxyCall(), failed = true)
        advanceTimeBy(NetworkStatus.FAILURE_EXPIRY_MS - 1_000)
        runCurrent()
        assertTrue(s.proxyFailed.value)
        advanceTimeBy(2_000)
        runCurrent()
        assertFalse(s.proxyFailed.value)
    }
}
