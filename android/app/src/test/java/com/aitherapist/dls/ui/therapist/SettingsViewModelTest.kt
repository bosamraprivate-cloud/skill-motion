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
class SettingsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun settings() = FakeTherapistSettings(pin = "1111", url = "https://old.example.dev", secret = "old-secret")

    private val SettingsViewModel.content get() = uiState.value as SettingsUiState.Content

    private fun SettingsViewModel.changePin(current: String, new: String, confirm: String) {
        onEvent(SettingsEvent.CurrentPinChanged(current))
        onEvent(SettingsEvent.NewPinChanged(new))
        onEvent(SettingsEvent.ConfirmPinChanged(confirm))
        onEvent(SettingsEvent.ChangePin)
    }

    @Test
    fun `loads url and only whether a secret exists`() {
        val vm = SettingsViewModel(settings(), dispatcher)
        assertEquals("https://old.example.dev", vm.content.proxyUrl)
        assertTrue(vm.content.secretSet)
        assertEquals("", vm.content.newSecret)
        assertFalse(vm.content.toString().contains("old-secret"))
    }

    @Test
    fun `change pin with wrong current pin is rejected`() = runTest {
        val s = settings()
        val vm = SettingsViewModel(s, dispatcher)
        vm.changePin("9999", "2222", "2222")
        assertEquals(PinChangeError.WRONG_CURRENT, vm.content.pinError)
        assertEquals("", vm.content.currentPin)
        assertTrue(s.verifyPin("1111"))
        assertFalse(s.verifyPin("2222"))
    }

    @Test
    fun `change pin with mismatching new pins is rejected`() = runTest {
        val s = settings()
        val vm = SettingsViewModel(s, dispatcher)
        vm.changePin("1111", "2222", "3333")
        assertEquals(PinChangeError.MISMATCH, vm.content.pinError)
        assertTrue(s.verifyPin("1111"))
    }

    @Test
    fun `change pin with right current pin makes the new one work and the old one fail`() = runTest {
        val s = settings()
        val vm = SettingsViewModel(s, dispatcher)
        vm.changePin("1111", "246802", "246802")
        assertTrue(vm.content.pinChanged)
        assertNull(vm.content.pinError)
        assertEquals("", vm.content.newPin)
        assertTrue(s.verifyPin("246802"))
        assertFalse(s.verifyPin("1111"))
    }

    @Test
    fun `three digit new pin is rejected and http url is not saved`() = runTest {
        val s = settings()
        val vm = SettingsViewModel(s, dispatcher)
        vm.changePin("1111", "123", "123")
        assertFalse(vm.content.pinChanged)
        assertTrue(s.verifyPin("1111"))
        vm.onEvent(SettingsEvent.ProxyUrlChanged("http://new.example.dev"))
        vm.onEvent(SettingsEvent.SaveProxyUrl)
        assertEquals("https://old.example.dev", s.url)
    }

    @Test
    fun `pin change keeps a typed secret and the secret is trimmed when saved`() {
        val s = settings()
        val vm = SettingsViewModel(s, dispatcher)
        vm.onEvent(SettingsEvent.SecretChanged(" typed "))
        vm.changePin("1111", "2222", "2222")
        assertTrue(vm.content.pinChanged)
        assertEquals(" typed ", vm.content.newSecret)
        vm.onEvent(SettingsEvent.SaveSecret)
        assertEquals("typed", s.secret)
    }

    @Test
    fun `short pins keep change disabled`() {
        val vm = SettingsViewModel(settings(), dispatcher)
        vm.onEvent(SettingsEvent.CurrentPinChanged("1111"))
        vm.onEvent(SettingsEvent.NewPinChanged("12"))
        vm.onEvent(SettingsEvent.ConfirmPinChanged("12"))
        assertFalse(vm.content.canChangePin)
    }

    @Test
    fun `invalid url cannot be saved and a valid one is stored`() {
        val s = settings()
        val vm = SettingsViewModel(s, dispatcher)
        vm.onEvent(SettingsEvent.ProxyUrlChanged("ftp://x"))
        assertFalse(vm.content.canSaveProxy)
        vm.onEvent(SettingsEvent.SaveProxyUrl)
        assertEquals("https://old.example.dev", s.url)
        vm.onEvent(SettingsEvent.ProxyUrlChanged("https://new.example.dev "))
        vm.onEvent(SettingsEvent.SaveProxyUrl)
        assertEquals("https://new.example.dev", s.url)
        assertTrue(vm.content.proxySaved)
    }

    @Test
    fun `replacing the secret stores it and clears the field`() {
        val s = settings()
        val vm = SettingsViewModel(s, dispatcher)
        vm.onEvent(SettingsEvent.SaveSecret)
        assertEquals("old-secret", s.secret)
        vm.onEvent(SettingsEvent.SecretChanged("new-secret"))
        vm.onEvent(SettingsEvent.SaveSecret)
        assertEquals("new-secret", s.secret)
        assertEquals("", vm.content.newSecret)
        assertTrue(vm.content.secretSaved)
    }

    @Test
    fun `no secret yet shows as not set`() {
        val vm = SettingsViewModel(FakeTherapistSettings(pin = "1111"), dispatcher)
        assertFalse(vm.content.secretSet)
        assertEquals("", vm.content.proxyUrl)
    }

    @Test
    fun `write failure shows error and retry reloads`() {
        val s = settings()
        val vm = SettingsViewModel(s, dispatcher)
        s.failWrites = true
        vm.onEvent(SettingsEvent.ProxyUrlChanged("https://new.example.dev"))
        vm.onEvent(SettingsEvent.SaveProxyUrl)
        assertEquals(SettingsUiState.Error("IOException"), vm.uiState.value)
        vm.onEvent(SettingsEvent.Retry)
        assertEquals("https://old.example.dev", vm.content.proxyUrl)
    }
}
