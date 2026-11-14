package com.aitherapist.dls.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aitherapist.dls.scoring.StepLevel
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Story 5.2: corrections upsert on (sessionId, stepId), delete restores the automatic level, null = not scored. */
@RunWith(AndroidJUnit4::class)
class CorrectionTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "correction-${UUID.randomUUID()}.db"
    private lateinit var db: AppDatabase
    private lateinit var repo: ResultsRepository

    @Before
    fun setUp() = runBlocking {
        db = AppDatabase.build(context, ByteArray(32) { 3 }, dbName)
        repo = ResultsRepository(DatabaseHolder { db })
        db.childDao().insert(ChildEntity("c", "n", "C-1", 6, characterVariant = ChildCharacter.GIRL, createdAt = 0))
        db.sessionDao().insert(SessionEntity("s", "c", "t", SessionKind.ASSESSMENT, SessionState.DONE, 0, 1, "v", "3", 0, 1, 1))
        db.attemptDao().insert(
            AttemptEntity("a", "s", "st", 1, 1, false, AttemptVerdict.NOT_DONE, "r", VerifierKind.POSE, null, null, 5, emptyList(), null, 1)
        )
        db.attemptDao().insert(
            AttemptEntity("b", "s", "st", 2, 2, false, AttemptVerdict.DONE, "r", VerifierKind.POSE, null, null, 5, emptyList(), null, 2)
        )
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(dbName)
    }

    private suspend fun level(): StepLevel? = repo.results("c").single().levels["st"]

    @Test
    fun upsertReplacesAndDeleteRestores() = runBlocking {
        assertEquals(StepLevel.TWO, level())
        repo.addCorrection(CorrectionEntity("k1", "s", "st", 3, null, 10))
        assertEquals(StepLevel.THREE, level())
        repo.addCorrection(CorrectionEntity("k2", "s", "st", 1, null, 11))
        assertEquals(1, db.correctionDao().forSession("s").size)
        assertEquals(StepLevel.ONE, level())
        repo.addCorrection(CorrectionEntity("k3", "s", "st", null, null, 12))
        assertNull(db.correctionDao().forSession("s").single().level)
        assertEquals(StepLevel.NOT_SCORED, level())
        repo.clearCorrection("s", "st")
        assertEquals(0, db.correctionDao().forSession("s").size)
        assertEquals(StepLevel.TWO, level())
    }
}
