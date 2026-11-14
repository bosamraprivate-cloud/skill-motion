package com.aitherapist.dls.data

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipStoreTest {
    private val dir = Files.createTempDirectory("files").toFile()
    private val store = ClipStore(dir)

    @Test
    fun `save replaces an existing clip and leaves no temp file`() {
        store.save("a.mp4", ByteArrayInputStream("old".toByteArray()))
        store.save("a.mp4", ByteArrayInputStream("new".toByteArray()))
        assertEquals("new", store.file("a.mp4").readText())
        assertEquals(listOf("a.mp4"), store.file("a.mp4").parentFile!!.list()!!.toList())
    }

    @Test
    fun `a failed copy keeps the old clip`() {
        store.save("a.mp4", ByteArrayInputStream("old".toByteArray()))
        val broken =
            object : InputStream() {
                override fun read(): Int = error("read failed")
            }
        assertTrue(runCatching { store.save("a.mp4", broken) }.isFailure)
        assertEquals("old", store.file("a.mp4").readText())
        assertFalse(store.file("a.mp4.tmp").exists())
    }

    @Test
    fun `path names are refused`() {
        listOf("../x.mp4", "a/b.mp4", "a\\b.mp4", "", "..").forEach { assertTrue(it, runCatching { store.file(it) }.isFailure) }
    }
}
