package com.aitherapist.dls

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.withPackage
import com.lemonappdev.konsist.api.verify.assertFalse
import org.junit.Test

/** Package rules from the architecture spine (Invariants & Rules diagram, AD-12). */
class ArchitectureTest {
    private val productionFiles = Konsist.scopeFromProduction().files

    @Test
    fun `core packages import nothing from android or androidx`() {
        productionFiles
            .withPackage(*CORE_PACKAGES)
            .assertFalse(additionalMessage = "Core packages must stay pure Kotlin (no android.* or androidx.* imports).") { file ->
                file.imports.any { it.name.startsWith("android.") || it.name.startsWith("androidx.") }
            }
    }

    @Test
    fun `pure vision core files import nothing from android or androidx`() {
        productionFiles
            .withPackage("$ROOT.vision..")
            .filter { it.name in PURE_VISION_FILES }
            .assertFalse(additionalMessage = "Vision core files must stay pure Kotlin.") { file ->
                file.imports.any { it.name.startsWith("android.") || it.name.startsWith("androidx.") }
            }
    }

    @Test
    fun `session core files import nothing from android or androidx`() {
        productionFiles
            .withPackage("$ROOT.session..")
            .filter { it.name in PURE_SESSION_FILES }
            .assertFalse(additionalMessage = "Session core files must stay pure Kotlin.") { file ->
                file.imports.any { it.name.startsWith("android.") || it.name.startsWith("androidx.") }
            }
    }

    @Test
    fun `no spine package outside ui depends on ui`() {
        // The app entry points in the root package (Application, Activity) host the UI and are exempt.
        productionFiles
            .withPackage(*NON_UI_PACKAGES)
            .assertFalse(additionalMessage = "Nothing depends on ui; move the dependency the other way.") { file ->
                file.imports.any { it.name.startsWith("$ROOT.ui.") }
            }
    }

    @Test
    fun `only data touches DAOs`() {
        productionFiles
            .filter { file -> file.packagee?.name.orEmpty().let { it != "$ROOT.data" && !it.startsWith("$ROOT.data.") } }
            .assertFalse(additionalMessage = "Only data/ may use DAOs or the database; go through a repository.") { file ->
                file.imports.any {
                    it.name == "$ROOT.data.*" ||
                        (it.name.startsWith("$ROOT.data.") && it.name.substringAfterLast('.') in DATA_INTERNALS)
                }
            }
    }

    private companion object {
        val DATA_INTERNALS = setOf("ChildDao", "SessionDao", "AttemptDao", "AppDatabase")
        const val ROOT = "com.aitherapist.dls"
        val CORE_PACKAGES = arrayOf("$ROOT.scoring..", "$ROOT.rewards..", "$ROOT.catalog..")
        val PURE_VISION_FILES = setOf("Geometry", "Verifier", "VisibilityGate", "PoseVerifier", "LandmarkModels", "FrameCropper")
        val PURE_SESSION_FILES = setOf("WindowRules", "Movement", "AttemptWindow", "PromptLadder")
        val NON_UI_PACKAGES =
            arrayOf("catalog", "data", "camera", "vision", "session", "scoring", "training", "rewards", "audio", "replay")
                .map { "$ROOT.$it.." }
                .toTypedArray()
    }
}
