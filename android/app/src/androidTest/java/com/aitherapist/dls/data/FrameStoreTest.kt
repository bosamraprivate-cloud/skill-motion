package com.aitherapist.dls.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.UUID
import javax.crypto.AEADBadTagException
import kotlin.random.Random
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FrameStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dir = File(context.cacheDir, "frames-test-${UUID.randomUUID()}")
    private val keys = KeyManager("dls_test_frames")
    private val store = FrameStore(dir, keys)

    @After
    fun tearDown() {
        dir.deleteRecursively()
        keys.deleteKey()
    }

    @Test
    fun roundTripUsesDocumentedPathAndLayout() {
        val jpeg = Random.nextBytes(5000)
        val file = store.write("s1", "a1", 2, jpeg)
        assertEquals(File(dir, "frames/s1/a1-2.bin"), file)
        assertEquals(KeyManager.IV_BYTES + jpeg.size + 16, file.length().toInt())
        assertFalse(file.readBytes().copyOfRange(12, 12 + 64).contentEquals(jpeg.copyOf(64)))
        assertArrayEquals(jpeg, store.read("s1", "a1", 2))
    }

    @Test
    fun tamperedFrameFailsWithBadTag() {
        val file = store.write("s1", "a1", 0, Random.nextBytes(1000))
        val bytes = file.readBytes()
        bytes[bytes.size / 2] = (bytes[bytes.size / 2].toInt() xor 1).toByte()
        file.writeBytes(bytes)
        try {
            store.read("s1", "a1", 0)
            fail("Tampered frame was read")
        } catch (expected: AEADBadTagException) {
            // surfaced, no partial bytes
        }
    }

    @Test
    fun deleteSessionRemovesFolder() {
        store.write("s2", "a", 0, byteArrayOf(1))
        store.deleteSession("s2")
        assertFalse(File(dir, "frames/s2").exists())
    }

    @Test
    fun missingFrameReadsNull() {
        assertNull(store.read("s3", "a", 0))
    }

    @Test
    fun truncatedFrameFailsWithBadTag() {
        val file = store.write("s4", "a", 0, byteArrayOf(1, 2, 3))
        file.writeBytes(ByteArray(5))
        try {
            store.read("s4", "a", 0)
            fail("Truncated frame was read")
        } catch (expected: AEADBadTagException) {
            // surfaced, no bytes
        }
    }

    @Test
    fun unsafeIdsAndNegativeIndexAreRejected() {
        val marker = File(dir, "keep.txt").apply {
            parentFile?.mkdirs()
            writeText("x")
        }
        listOf("..", "a/b", "", ".", "a.b").forEach { bad ->
            assertRejected { store.deleteSession(bad) }
            assertRejected { store.write(bad, "a", 0, byteArrayOf(1)) }
            assertRejected { store.read("s", bad, 0) }
        }
        assertRejected { store.write("s", "a", -1, byteArrayOf(1)) }
        assertTrue(marker.exists())
    }

    private fun assertRejected(block: () -> Unit) {
        try {
            block()
            fail("Unsafe input accepted")
        } catch (expected: IllegalArgumentException) {
            // rejected
        }
    }
}
