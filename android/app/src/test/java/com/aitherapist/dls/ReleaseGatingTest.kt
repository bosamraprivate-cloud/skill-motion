package com.aitherapist.dls

import com.lemonappdev.konsist.api.Konsist
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Debug-only tools must stay behind BuildConfig.DEBUG so release builds never show or log them. */
class ReleaseGatingTest {
    private val files = Konsist.scopeFromProduction().files

    private fun text(name: String): String = files.first { it.name == name }.text

    @Test
    fun `settings gates replay, try step and the developer tools on debug`() {
        val settings = text("SettingsScreen")
        assertTrue(settings.contains("val debug = BuildConfig.DEBUG"))
        assertTrue(settings.contains("page == SettingsPage.REPLAY && debug"))
        assertTrue(settings.contains("showTryStep && debug"))
        assertTrue(Regex("""if \(debug\) \{\s*AdvancedTools\(""").containsMatchIn(settings))
    }

    @Test
    fun `character preview route is registered only in debug`() {
        assertTrue(text("AppNavigation").contains("if (BuildConfig.DEBUG) entry<CharacterPreviewKey>"))
    }

    @Test
    fun `debug result strip draws nothing in release`() {
        assertTrue(text("DebugResultStrip").contains("if (!BuildConfig.DEBUG) return"))
    }

    @Test
    fun `diagnostics developer section defaults to debug`() {
        assertTrue(text("DiagnosticsScreen").contains("developer: Boolean = BuildConfig.DEBUG"))
    }

    @Test
    fun `every DlsVerdict log site is gated on debug`() {
        files.filter { it.text.contains("\"DlsVerdict\"") }.forEach { file ->
            assertTrue("${file.name} logs DlsVerdict without a debug gate", file.text.contains("BuildConfig.DEBUG"))
        }
        val runnerLog = Regex("""if \(com\.aitherapist\.dls\.BuildConfig\.DEBUG\) \{\s*android\.util\.Log\.d\("DlsVerdict"""")
        assertTrue(runnerLog.containsMatchIn(text("ChildModeModule")))
    }

    @Test
    fun `release R8 strips debug logs`() {
        val rules = File(System.getProperty("user.dir"), "proguard-rules.pro").readText()
        assertTrue(Regex("""-assumenosideeffects class android\.util\.Log \{[^}]*d\(\.\.\.\);""").containsMatchIn(rules))
    }
}
