package com.aitherapist.dls.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "test-${UUID.randomUUID()}.db"
    private val keys = KeyManager("dls_test_db")
    private lateinit var settingsFile: File

    @Before
    fun setUp() {
        settingsFile = File(context.filesDir, "datastore/test-${UUID.randomUUID()}.preferences_pb")
    }

    @After
    fun tearDown() {
        storeScope.cancel()
        context.deleteDatabase(dbName)
        settingsFile.delete()
        keys.deleteKey()
    }

    private val storeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private fun settings() = SecureSettings(PreferenceDataStoreFactory.create(scope = storeScope) { settingsFile }, keys)

    private fun child(id: String = UUID.randomUUID().toString(), code: String = "C-$id") =
        ChildEntity(id = id, name = "Test", code = code, ageYears = 6, characterVariant = ChildCharacter.GIRL, createdAt = 1L)

    private fun session(childId: String) = SessionEntity(
        id = UUID.randomUUID().toString(),
        childId = childId,
        taskId = "task",
        kind = SessionKind.ASSESSMENT,
        state = SessionState.RUNNING,
        startedAt = 1L,
        endedAt = null,
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
        framePaths = listOf("a-0.bin"),
        clipFile = null,
        createdAt = 1L
    )

    @Test
    fun firstRunGeneratesPassphraseAndSecondRunReadsEarlierRows() = runBlocking {
        val s = settings()
        val first = s.databasePassphrase()
        assertEquals(32, first.size)
        val c = child()
        AppDatabase.build(context, first, dbName).apply {
            childDao().insert(c)
            close()
        }
        val second = s.databasePassphrase()
        assertArrayEquals(first, second)
        val db = AppDatabase.build(context, second, dbName)
        assertEquals(c, db.childDao().byId(c.id))
        db.close()
    }

    @Test
    fun wrongPassphraseFailsToOpen() = runBlocking {
        AppDatabase.build(context, ByteArray(32) { 1 }, dbName).apply {
            childDao().insert(child())
            close()
        }
        val wrong = AppDatabase.build(context, ByteArray(32) { 2 }, dbName)
        try {
            wrong.childDao().byId("x")
            fail("Opened with the wrong passphrase")
        } catch (expected: Exception) {
            // SQLCipher rejects the key: no data.
        } finally {
            wrong.close()
        }
    }

    @Test
    fun databaseFileIsNotPlainSqlite() = runBlocking {
        AppDatabase.build(context, ByteArray(32) { 3 }, dbName).apply {
            childDao().insert(child())
            close()
        }
        val file = context.getDatabasePath(dbName)
        val header = file.readBytes().copyOf(16)
        assertFalse(String(header, Charsets.US_ASCII).startsWith("SQLite format 3"))
        try {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use {
                it.rawQuery("SELECT count(*) FROM child", null).close()
            }
            fail("Plain SQLite read the encrypted DB")
        } catch (expected: Exception) {
            // Not readable without the key.
        }
    }

    @Test
    fun childDeleteCascadesSessionsAttemptsAndFrames() = runBlocking {
        val db = AppDatabase.build(context, ByteArray(32) { 4 }, dbName)
        val frames = FrameStore(File(context.cacheDir, "fs-${UUID.randomUUID()}"), keys)
        val repo = ChildRepository(DatabaseHolder { db }, frames)
        val sessions = SessionRepository(DatabaseHolder { db }, frames)
        val c = child()
        repo.add(c)
        val s = session(c.id)
        sessions.add(s)
        val a = attempt(s.id)
        sessions.addAttempt(a)
        val frame = frames.write(s.id, a.id, 0, byteArrayOf(1, 2, 3))
        assertEquals(listOf(a), sessions.attempts(s.id, "step"))
        assertEquals(listOf(c), repo.observeChildren().first())

        repo.delete(c.id)

        assertNull(repo.get(c.id))
        assertNull(sessions.get(s.id))
        assertTrue(sessions.attempts(s.id).isEmpty())
        assertFalse(frame.exists())
        db.close()
    }

    @Test
    fun evalChildIsHiddenAndCodeIsUnique() = runBlocking {
        val db = AppDatabase.build(context, ByteArray(32) { 5 }, dbName)
        val repo = ChildRepository(DatabaseHolder { db }, FrameStore(context.cacheDir, keys))
        repo.add(child(code = "A"))
        repo.add(child(code = "E").copy(isEval = true))
        assertEquals(listOf("A"), repo.observeChildren().first().map { it.code })
        try {
            repo.add(child(code = "A"))
            fail("Duplicate code accepted")
        } catch (expected: Exception) {
            // unique child.code
        }
        db.close()
    }

    @Test
    fun resumableQueriesReturnOnlyRunningAndPausedNewestFirstPerChild() = runBlocking {
        val db = AppDatabase.build(context, ByteArray(32) { 6 }, dbName)
        val a = child()
        val b = child()
        db.childDao().insert(a)
        db.childDao().insert(b)
        var t = 0L
        val all =
            listOf(a, b).flatMap { c ->
                SessionState.entries.map { st -> session(c.id).copy(state = st, startedAt = ++t) }
            }
        all.forEach { db.sessionDao().insert(it) }
        val open = setOf(SessionState.RUNNING, SessionState.PAUSED)
        val expectedA = all.filter { it.childId == a.id && it.state in open }.sortedByDescending { it.startedAt }
        assertEquals(expectedA, db.sessionDao().resumableForChild(a.id))
        val expectedAll = all.filter { it.state in open }.sortedByDescending { it.startedAt }
        assertEquals(expectedAll, db.sessionDao().observeResumable().first())
        db.close()
    }

    @Test
    fun evalChildAndReplaySessionsAreHiddenFromChildrenResumableAndReports() = runBlocking {
        val db = AppDatabase.build(context, ByteArray(32) { 7 }, dbName)
        val holder = DatabaseHolder { db }
        val replay = ReplayRepository(holder)
        val evalId = replay.evalChildId()
        assertEquals(evalId, replay.evalChildId()) // created once
        val visible = child()
        db.childDao().insert(visible)
        val evalSession = session(evalId).copy(kind = SessionKind.REPLAY)
        val strayReplay = session(visible.id).copy(kind = SessionKind.REPLAY)
        val evalAssessment = session(evalId)
        val live = session(visible.id)
        listOf(evalSession, strayReplay, evalAssessment, live).forEach { db.sessionDao().insert(it) }
        assertEquals(
            listOf(visible.id),
            ChildRepository(holder, FrameStore(context.cacheDir, keys)).observeChildren().first().map {
                it.id
            }
        )
        assertEquals(listOf(live), db.sessionDao().observeResumable().first())
        assertEquals(listOf(live), db.sessionDao().resumableForChild(visible.id))
        assertEquals(listOf(live), db.sessionDao().forChild(visible.id))
        assertTrue(db.sessionDao().forChild(evalId).all { it.kind == SessionKind.ASSESSMENT })
        assertEquals(setOf(evalSession.id, strayReplay.id), db.sessionDao().replaySessions().map { it.id }.toSet())
        // latestReplaySession prefers a finished session over a newer running one.
        val done = session(evalId).copy(kind = SessionKind.REPLAY, state = SessionState.DONE, startedAt = 5L)
        val newerRunning = session(evalId).copy(kind = SessionKind.REPLAY, startedAt = 9L)
        db.sessionDao().insert(done)
        db.sessionDao().insert(newerRunning)
        assertEquals(done.id, replay.latestReplaySession()?.id)
        db.close()
    }
}
