package com.aitherapist.dls.catalog

import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CharacterAssetsTest {
    private val text =
        """
        {"variants": [
          {"id": "v1", "reactions": [{"state": "r1", "file": "character/v1/react/r1.webp"}],
           "tasks": [{"id": "t.a", "steps": [
             {"id": "t.a.01", "clip": "character/v1/steps/t.a.01.webp", "still": "character/v1/steps/t.a.01.still.webp"},
             {"id": "t.a.02", "clip": null, "still": "character/v1/steps/t.a.02.still.webp"}]}]},
          {"id": "v2", "reactions": [], "tasks": []}
        ]}
        """.trimIndent()

    @Test
    fun `decodes variants reactions and ordered steps`() {
        val index = CharacterAssets.decode(text)
        assertEquals(listOf("v1", "v2"), index.variants.map { it.id })
        val steps = index.variants[0].tasks.single().steps
        assertEquals(listOf("t.a.01", "t.a.02"), steps.map { it.id })
        assertNull(steps[1].clip)
        assertEquals(ReactionClip("r1", "character/v1/react/r1.webp"), index.variants[0].reactions.single())
    }

    @Test
    fun `lists every referenced file`() {
        assertEquals(
            listOf(
                "character/v1/react/r1.webp",
                "character/v1/steps/t.a.01.webp",
                "character/v1/steps/t.a.01.still.webp",
                "character/v1/steps/t.a.02.still.webp"
            ),
            CharacterAssets.files(CharacterAssets.decode(text))
        )
    }

    @Test
    fun `shipped index decodes and every referenced file exists`() {
        // Gradle runs unit tests with the module directory (android/app) as working directory.
        val assets = java.io.File("src/main/assets")
        val index = CharacterAssets.decode(java.io.File(assets, CharacterAssets.INDEX_PATH).readText())
        val missing = CharacterAssets.files(index).filterNot { java.io.File(assets, it).isFile }
        assertEquals(emptyList<String>(), missing)
    }

    @Test(expected = SerializationException::class)
    fun `unknown key fails`() {
        CharacterAssets.decode("""{"variants": [], "extra": 1}""")
    }
}
