package com.aitherapist.dls.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** v1 -> v2 keeps attempt rows and adds an empty `modelReason` (Story 3.6). */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun migrate1To2KeepsRows() {
        helper.createDatabase(DB, 1).use { db ->
            db.execSQL(
                "INSERT INTO child (id, name, code, ageYears, effectLevel, characterVariant, isEval, createdAt) " +
                    "VALUES ('c', 'n', 'C1', 5, 'calm', 'boy', 0, 0)"
            )
            db.execSQL(
                "INSERT INTO session (id, childId, taskId, kind, state, startedAt, endedAt, catalogVersion, rulesVersion, " +
                    "stepIndex, attemptNumber, promptLevel) VALUES ('s', 'c', 't', 'assessment', 'done', 0, 0, 'v', '1', 0, 1, 1)"
            )
            db.execSQL(
                "INSERT INTO attempt (id, sessionId, stepId, number, promptLevel, helpPressed, verdict, reason, verifier, modelId, " +
                    "promptVersion, latencyMs, framePaths, clipFile, createdAt) VALUES ('a', 's', 'st', 1, 1, 0, 'done', 'cup at lip', " +
                    "'cloud', 'm', 'p', 5, '[]', NULL, 0)"
            )
        }
        helper.runMigrationsAndValidate(DB, 2, true, AppDatabase.MIGRATION_1_2).use { db ->
            db.query("SELECT reason, modelReason FROM attempt WHERE id = 'a'").use { c ->
                c.moveToFirst()
                assertEquals("cup at lip", c.getString(0))
                assertNull(c.getString(1))
            }
        }
    }

    private companion object {
        const val DB = "migration-test.db"
    }
}
