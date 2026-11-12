package com.aitherapist.dls.catalog

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The debug-only spike step files must decode: pose steps to distanceBelow, cloud steps to question (Stories 1.3, 1.5). */
class SpikeAssetDecodingTest {
    private val files = File("src/debug/assets/spike").listFiles { f -> f.extension == "json" }.orEmpty()

    @Test
    fun `spike assets decode to supported steps`() {
        assertTrue("no spike assets found", files.isNotEmpty())
        files.forEach { file ->
            val step = CatalogJson.decodeStep(file.readText())
            val ok =
                (step.method == Method.POSE && step.check is Check.DistanceBelow) ||
                    (step.method == Method.CLOUD && step.check is Check.Question)
            assertTrue(file.name, ok)
        }
    }

    @Test
    fun `both spike steps are bundled`() {
        val steps = files.map { CatalogJson.decodeStep(it.readText()) }.associateBy { it.method }
        assertEquals(setOf(Method.POSE, Method.CLOUD), steps.keys)
        assertTrue((steps.getValue(Method.CLOUD).check as Check.Question).text.isNotBlank())
    }
}
