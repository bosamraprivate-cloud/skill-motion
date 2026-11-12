package com.aitherapist.dls.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Story 2.11: deleting a child removes its rows and frame folders; codes compare normalised. */
@RunWith(AndroidJUnit4::class)
class ChildDeleteTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "child-delete-${UUID.randomUUID()}.db"
    private val keys = KeyManager("dls_test_child_delete")
    private lateinit var db: AppDatabase
    private lateinit var root: File
    private lateinit var frames: FrameStore
    private lateinit var children: ChildRepository
    private lateinit var sessions: SessionRepository

    @Before
    fun setUp() {
        db = AppDatabase.build(context, ByteArray(32) { 7 }, dbName)
        root = File(context.cacheDir, "fs-${UUID.randomUUID()}")
        frames = FrameStore(root, keys)
        children = ChildRepository(DatabaseHolder { db }, frames)
        sessions = SessionRepository(DatabaseHolder { db }, frames)
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(dbName)
        root.deleteRecursively()
        keys.deleteKey()
    }

    private fun child(code: String) = ChildEntity(
        id = UUID.randomUUID().toString(),
        name = "Test",
        code = code,
        ageYears = 6,
        characterVariant = ChildCharacter.BOY,
        createdAt = 1L
    )

    private fun session(childId: String) = SessionEntity(
        id = UUID.randomUUID().toString(),
        childId = childId,
        taskId = "task",
        kind = SessionKind.ASSESSMENT,
        state = SessionState.DONE,
        startedAt = 1L,
        endedAt = 2L,
        catalogVersion = "cv",
        rulesVersion = "rv",
        stepIndex = 0,
        attemptNumber = 1,
        promptLevel = 1
    )

    private fun attempt(sessionId: String) = AttemptEntity(
        id = UUID.randomUUID().toString(),
        sessionId = sessionId,
        stepId = "step",
        number = 1,
        promptLevel = 1,
        helpPressed = false,
        verdict = AttemptVerdict.CANNOT_TELL,
        reason = "not_judgeable",
        verifier = VerifierKind.NONE,
        modelId = null,
        promptVersion = null,
        latencyMs = 0,
        framePaths = emptyList(),
        clipFile = null,
        createdAt = 1L
    )

    /** Adds a child with two sessions, each with an attempt and a frame; returns the session ids and frame files. */
    private suspend fun seed(c: ChildEntity): Pair<List<String>, List<File>> {
        children.add(c)
        val ids = mutableListOf<String>()
        val files = mutableListOf<File>()
        repeat(2) {
            val s = session(c.id)
            sessions.add(s)
            val a = attempt(s.id)
            sessions.addAttempt(a)
            files += frames.write(s.id, a.id, 0, byteArrayOf(1, 2, 3))
            ids += s.id
        }
        return ids to files
    }

    @Test
    fun deleteRemovesRowsAndFrameFoldersOfThatChildOnly() = runBlocking {
        val gone = child("A1")
        val kept = child("B2")
        val (goneSessions, goneFiles) = seed(gone)
        val (keptSessions, keptFiles) = seed(kept)

        children.delete(gone.id)

        assertNull(children.get(gone.id))
        goneSessions.forEach {
            assertNull(sessions.get(it))
            assertTrue(sessions.attempts(it).isEmpty())
        }
        goneFiles.forEach {
            assertFalse(it.exists())
            assertFalse(it.parentFile!!.exists())
        }
        assertEquals(listOf(kept), children.observeChildren().first())
        keptSessions.forEach { assertEquals(1, sessions.attempts(it).size) }
        keptFiles.forEach { assertTrue(it.exists()) }
    }

    @Test
    fun codeExistsIgnoresCaseAndExcludesSelf() = runBlocking {
        val c = child("AB1")
        children.add(c)
        assertTrue(children.codeExists("aB1", null))
        assertTrue(children.codeExists(" AB1 ", "other"))
        assertFalse(children.codeExists("ab1", c.id))
        assertFalse(children.codeExists("zz", null))
    }
}
