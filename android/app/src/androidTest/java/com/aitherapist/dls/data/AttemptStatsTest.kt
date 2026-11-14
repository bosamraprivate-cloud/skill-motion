package com.aitherapist.dls.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Story 5.5: the cannot-tell rate counts finished assessment attempts of real children and excludes verifier `none`. */
@RunWith(AndroidJUnit4::class)
class AttemptStatsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "stats-${UUID.randomUUID()}.db"
    private lateinit var db: AppDatabase
    private lateinit var repo: AttemptStatsRepository

    @Before
    fun setUp() {
        db = AppDatabase.build(context, ByteArray(32) { 4 }, dbName)
        repo = AttemptStatsRepository(DatabaseHolder { db })
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(dbName)
    }

    private fun attempt(id: String, session: String, verdict: AttemptVerdict, verifier: VerifierKind) =
        AttemptEntity(id, session, "st", 1, 1, false, verdict, "r", verifier, null, null, 5, emptyList(), null, 1)

    @Test
    fun emptyIsZero() = runBlocking {
        assertEquals(CannotTellCounts(0, 0), repo.cannotTellCounts())
    }

    @Test
    fun countsOnlyFinishedAssessmentAttemptsOfRealChildren() = runBlocking {
        db.childDao().insert(ChildEntity("c", "n", "C-1", 6, characterVariant = ChildCharacter.GIRL, createdAt = 0))
        db.childDao().insert(ChildEntity("e", "eval", "C-2", 6, characterVariant = ChildCharacter.BOY, isEval = true, createdAt = 0))
        db.sessionDao().insert(SessionEntity("s", "c", "t", SessionKind.ASSESSMENT, SessionState.DONE, 0, 1, "v", "3", 0, 1, 1))
        db.sessionDao().insert(SessionEntity("r", "c", "t", SessionKind.REPLAY, SessionState.DONE, 0, 1, "v", "3", 0, 1, 1))
        db.sessionDao().insert(SessionEntity("p", "c", "t", SessionKind.ASSESSMENT, SessionState.PAUSED, 0, null, "v", "3", 0, 1, 1))
        db.sessionDao().insert(SessionEntity("x", "e", "t", SessionKind.ASSESSMENT, SessionState.DONE, 0, 1, "v", "3", 0, 1, 1))
        db.attemptDao().insert(attempt("a1", "s", AttemptVerdict.DONE, VerifierKind.POSE))
        db.attemptDao().insert(attempt("a2", "s", AttemptVerdict.CANNOT_TELL, VerifierKind.CLOUD))
        db.attemptDao().insert(attempt("a3", "s", AttemptVerdict.NOT_DONE, VerifierKind.HAND))
        db.attemptDao().insert(attempt("a4", "s", AttemptVerdict.CANNOT_TELL, VerifierKind.NONE))
        db.attemptDao().insert(attempt("a5", "r", AttemptVerdict.CANNOT_TELL, VerifierKind.POSE))
        db.attemptDao().insert(attempt("a7", "p", AttemptVerdict.CANNOT_TELL, VerifierKind.POSE))
        db.attemptDao().insert(attempt("a6", "x", AttemptVerdict.CANNOT_TELL, VerifierKind.POSE))
        val counts = repo.cannotTellCounts()
        assertEquals(CannotTellCounts(3, 1), counts)
        assertEquals(33, counts.percent)
    }
}
