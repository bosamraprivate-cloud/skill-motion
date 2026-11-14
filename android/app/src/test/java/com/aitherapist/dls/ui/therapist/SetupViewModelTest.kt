package com.aitherapist.dls.ui.therapist

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SetupViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm(settings: FakeTherapistSettings, url: String = "", secret: String = "") =
        SetupViewModel(settings, dispatcher, url, secret)

    private val SetupViewModel.content get() = uiState.value as SetupUiState.Content

    private fun SetupViewModel.enterPin(pin: String) {
        onEvent(SetupEvent.PinChanged(pin))
        onEvent(SetupEvent.Continue)
    }

    @Test
    fun `fresh install starts on welcome and nothing is saved`() {
        val settings = FakeTherapistSettings()
        val vm = vm(settings)
        assertEquals(SetupStep.WELCOME, vm.content.step)
        assertNull(settings.pinHash)
    }

    @Test
    fun `pin too short keeps continue disabled`() {
        val vm = vm(FakeTherapistSettings())
        vm.onEvent(SetupEvent.Continue)
        vm.onEvent(SetupEvent.PinChanged("123"))
        assertFalse(vm.content.canContinue)
        vm.onEvent(SetupEvent.Continue)
        assertEquals(SetupStep.PIN, vm.content.step)
        vm.onEvent(SetupEvent.PinChanged("1234"))
        assertTrue(vm.content.canContinue)
    }

    @Test
    fun `pin input keeps digits only and at most six`() {
        val vm = vm(FakeTherapistSettings())
        vm.onEvent(SetupEvent.Continue)
        vm.onEvent(SetupEvent.PinChanged("12a34567"))
        assertEquals("123456", vm.content.pin)
    }

    @Test
    fun `pin mismatch stays on the confirm step with a message and saves nothing`() {
        val settings = FakeTherapistSettings()
        val vm = vm(settings)
        vm.onEvent(SetupEvent.Continue)
        vm.enterPin("1234")
        vm.enterPin("4321")
        assertEquals(SetupStep.CONFIRM_PIN, vm.content.step)
        assertTrue(vm.content.pinMismatch)
        assertEquals("", vm.content.confirmPin)
        assertNull(settings.pinHash)
        vm.onEvent(SetupEvent.PinChanged("1"))
        assertFalse(vm.content.pinMismatch)
    }

    @Test
    fun `built-in connection skips the connection step and saves it with the pin`() {
        val settings = FakeTherapistSettings()
        val vm = vm(settings, url = "https://proxy.example.dev", secret = "built-in-secret")
        vm.onEvent(SetupEvent.Continue)
        vm.enterPin("1234")
        vm.enterPin("1234")
        assertEquals(SetupStep.ZONES, vm.content.step)
        assertEquals("https://proxy.example.dev", settings.url)
        assertEquals("built-in-secret", settings.secret)
        assertTrue(settings.pinHash != null)
    }

    @Test
    fun `invalid url keeps finish disabled`() {
        val vm = vm(FakeTherapistSettings())
        vm.onEvent(SetupEvent.Continue)
        vm.enterPin("1234")
        vm.enterPin("1234")
        assertEquals(SetupStep.PROXY, vm.content.step)
        vm.onEvent(SetupEvent.SecretChanged("s3cret"))
        vm.onEvent(SetupEvent.ProxyUrlChanged("http://proxy.example.dev"))
        assertFalse(vm.content.proxyUrlValid)
        assertFalse(vm.content.canContinue)
        vm.onEvent(SetupEvent.ProxyUrlChanged("https://"))
        assertFalse(vm.content.canContinue)
        vm.onEvent(SetupEvent.ProxyUrlChanged("https://proxy.example.dev"))
        assertTrue(vm.content.canContinue)
        vm.onEvent(SetupEvent.SecretChanged(" "))
        assertFalse(vm.content.canContinue)
    }

    @Test
    fun `setup done saves pin url and secret then reports done`() = runTest {
        val settings = FakeTherapistSettings()
        val vm = vm(settings)
        vm.onEvent(SetupEvent.Continue)
        vm.enterPin("123456")
        vm.enterPin("123456")
        vm.onEvent(SetupEvent.ProxyUrlChanged(" https://proxy.example.dev "))
        vm.onEvent(SetupEvent.SecretChanged("s3cret"))
        vm.onEvent(SetupEvent.Continue)
        // Saved; the optional zone step follows and can be skipped.
        assertEquals(SetupStep.ZONES, vm.content.step)
        assertEquals("", vm.content.secret)
        vm.onEvent(SetupEvent.Back)
        assertEquals(SetupStep.ZONES, vm.content.step)
        vm.onEvent(SetupEvent.Continue)
        assertEquals(SetupUiState.Done, vm.uiState.value)
        assertTrue(settings.verifyPin("123456"))
        assertEquals("https://proxy.example.dev", settings.url)
        assertEquals("s3cret", settings.secret)
    }

    @Test
    fun `digit and delete events edit the active pin`() {
        val vm = vm(FakeTherapistSettings())
        vm.onEvent(SetupEvent.Continue)
        "12345678".forEach { vm.onEvent(SetupEvent.Digit(it)) }
        assertEquals("123456", vm.content.pin)
        vm.onEvent(SetupEvent.Delete)
        vm.onEvent(SetupEvent.Continue)
        vm.onEvent(SetupEvent.Digit('1'))
        assertEquals("1", vm.content.confirmPin)
        assertEquals("12345", vm.content.pin)
    }

    @Test
    fun `secret is trimmed and urls okhttp accepts are valid`() {
        val settings = FakeTherapistSettings()
        val vm = vm(settings, url = "https://proxy_1.example.dev/", secret = " s3cret ")
        vm.onEvent(SetupEvent.Continue)
        vm.enterPin("1234")
        vm.enterPin("1234")
        assertTrue(vm.content.proxyUrlValid)
        vm.onEvent(SetupEvent.Continue)
        assertEquals("s3cret", settings.secret)
    }

    @Test
    fun `debug prefill fills the proxy step`() {
        val vm = vm(FakeTherapistSettings(), url = "https://pre.example.dev", secret = "pre")
        assertEquals("https://pre.example.dev", vm.content.proxyUrl)
        assertEquals("pre", vm.content.secret)
    }

    @Test
    fun `back from proxy restarts pin entry`() {
        val vm = vm(FakeTherapistSettings())
        vm.onEvent(SetupEvent.Continue)
        vm.enterPin("1234")
        vm.onEvent(SetupEvent.Back)
        assertEquals(SetupStep.PIN, vm.content.step)
        assertEquals("", vm.content.pin)
        vm.onEvent(SetupEvent.Back)
        assertEquals(SetupStep.WELCOME, vm.content.step)
        vm.onEvent(SetupEvent.Back)
        assertEquals(SetupStep.WELCOME, vm.content.step)
    }

    @Test
    fun `save failure shows error and retry returns to the form`() {
        val settings = FakeTherapistSettings().apply { failWrites = true }
        val vm = vm(settings, url = "https://proxy.example.dev", secret = "s")
        vm.onEvent(SetupEvent.Continue)
        vm.enterPin("1234")
        // Built-in connection: confirming the PIN saves directly (no connection step).
        vm.enterPin("1234")
        assertEquals(SetupUiState.Error("IOException"), vm.uiState.value)
        vm.onEvent(SetupEvent.Retry)
        assertEquals(SetupStep.CONFIRM_PIN, vm.content.step)
        assertFalse(vm.content.saving)
    }

    @Test
    fun `toString never prints pin or secret`() {
        val text = SetupUiState.Content(pin = "987654", secret = "topsecret").toString()
        assertFalse(text.contains("987654"))
        assertFalse(text.contains("topsecret"))
    }
}
