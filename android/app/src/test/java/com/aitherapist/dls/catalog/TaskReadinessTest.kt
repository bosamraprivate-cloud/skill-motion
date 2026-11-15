package com.aitherapist.dls.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskReadinessTest {
    private fun step(n: Int, media: Map<String, StepMedia?>) =
        CatalogStep("t.x.0$n", "a", "t.x", n, "", "", "", emptyList(), "", StepDetector(Method.POSE), null, media)

    private fun clip(variant: String, n: Int) =
        StepMedia("character/$variant/steps/t.x.0$n.webp", "character/$variant/steps/t.x.0$n.still.webp")

    private val task =
        CatalogTask(
            "t.x",
            "a",
            listOf(
                step(1, mapOf("boy" to clip("boy", 1), "girl" to clip("girl", 1))),
                step(2, mapOf("boy" to clip("boy", 2), "girl" to null))
            )
        )

    private var listings = 0

    /** Fake AssetManager.list: only the boy folder exists, holding both boy clips. */
    private val assets =
        CachedAssetPresence { dir ->
            listings++
            if (dir == "character/boy/steps") listOf("t.x.01.webp", "t.x.01.still.webp", "t.x.02.webp") else null
        }

    @Test
    fun `ready only when every step has its variant clip in the APK`() {
        assertTrue(TaskReadiness.isReady(task, "boy", assets))
        assertFalse("girl media missing in the catalogue", TaskReadiness.isReady(task, "girl", assets))
        assertEquals(setOf("boy"), TaskReadiness.readyVariants(task, assets))
    }

    @Test
    fun `a clip named in the catalogue but missing from the APK is not ready`() {
        val missing = CachedAssetPresence { listOf("t.x.01.webp") }
        assertFalse(TaskReadiness.isReady(task, "boy", missing))
    }

    @Test
    fun `a task with no steps or a step with only a still is not ready`() {
        assertFalse(TaskReadiness.isReady(CatalogTask("t.x", "a", emptyList()), "boy", assets))
        val stillOnly = CatalogTask("t.x", "a", listOf(step(1, mapOf("boy" to StepMedia(null, "character/boy/steps/t.x.01.still.webp")))))
        assertFalse(TaskReadiness.isReady(stillOnly, "boy", assets))
    }

    @Test
    fun `each folder is listed once`() {
        repeat(3) { TaskReadiness.readyVariants(task, assets) }
        assertEquals(2, listings) // boy folder + girl folder
    }
}
