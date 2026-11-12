package com.aitherapist.dls.catalog

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** The debug-only spike step files must decode to pose distanceBelow steps (Story 1.3). */
class SpikeAssetDecodingTest {
    @Test
    fun `spike assets decode to distanceBelow steps`() {
        val files = File("src/debug/assets/spike").listFiles { f -> f.extension == "json" }.orEmpty()
        assertTrue("no spike assets found", files.isNotEmpty())
        files.forEach { file ->
            val step = CatalogJson.decodeStep(file.readText())
            assertTrue(file.name, step.method == Method.POSE && step.check is Check.DistanceBelow)
        }
    }
}
