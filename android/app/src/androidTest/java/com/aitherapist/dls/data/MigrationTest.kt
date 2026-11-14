package com.aitherapist.dls.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** v1 -> v2 adds an empty `modelReason` (Story 3.6); v2 -> v3 adds `correction` and `session.nextTaskId` (Story 4.1). */
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

    @Test
    fun migrate2To3KeepsRowsAndAddsCorrectionAndNextTask() {
        helper.createDatabase(DB, 2).use { db ->
            db.execSQL(
                "INSERT INTO child (id, name, code, ageYears, effectLevel, characterVariant, isEval, createdAt) " +
                    "VALUES ('c', 'n', 'C1', 5, 'calm', 'boy', 0, 0)"
            )
            db.execSQL(
                "INSERT INTO session (id, childId, taskId, kind, state, startedAt, endedAt, catalogVersion, rulesVersion, " +
                    "stepIndex, attemptNumber, promptLevel) VALUES ('s', 'c', 't', 'assessment', 'done', 0, 0, 'v', '2', 0, 1, 1)"
            )
            db.execSQL(
                "INSERT INTO attempt (id, sessionId, stepId, number, promptLevel, helpPressed, verdict, reason, verifier, modelId, " +
                    "promptVersion, latencyMs, framePaths, clipFile, createdAt, modelReason) VALUES ('a', 's', 'st', 1, 1, 0, 'done', " +
                    "'cup at lip', 'pose', NULL, NULL, 5, '[]', NULL, 0, NULL)"
            )
        }
        helper.runMigrationsAndValidate(DB, 3, true, AppDatabase.MIGRATION_2_3).use { db ->
            db.query("SELECT taskId, nextTaskId FROM session WHERE id = 's'").use { c ->
                c.moveToFirst()
                assertEquals("t", c.getString(0))
                assertNull(c.getString(1))
            }
            db.query("SELECT reason FROM attempt WHERE id = 'a'").use { c ->
                c.moveToFirst()
                assertEquals("cup at lip", c.getString(0))
            }
            db.execSQL("INSERT INTO correction (id, sessionId, stepId, level, reason, createdAt) VALUES ('k', 's', 'st', NULL, NULL, 1)")
            db.query("SELECT COUNT(*) FROM correction").use { c ->
                c.moveToFirst()
                assertEquals(1, c.getInt(0))
            }
            db.execSQL("PRAGMA foreign_keys = ON")
            db.execSQL("DELETE FROM session WHERE id = 's'")
            db.query("SELECT COUNT(*) FROM correction").use { c ->
                c.moveToFirst()
                assertEquals("corrections cascade with their session", 0, c.getInt(0))
            }
        }
    }

    @Test
    fun migrate1To3Chained() {
        helper.createDatabase(DB, 1).use { db ->
            db.execSQL(
                "INSERT INTO child (id, name, code, ageYears, effectLevel, characterVariant, isEval, createdAt) " +
                    "VALUES ('c', 'n', 'C1', 5, 'calm', 'boy', 0, 0)"
            )
            db.execSQL(
                "INSERT INTO session (id, childId, taskId, kind, state, startedAt, endedAt, catalogVersion, rulesVersion, " +
                    "stepIndex, attemptNumber, promptLevel) VALUES ('s', 'c', 't', 'assessment', 'paused', 0, NULL, 'v', '1', 0, 1, 1)"
            )
        }
        helper.runMigrationsAndValidate(DB, 3, true, *AppDatabase.MIGRATIONS).use { db ->
            db.query("SELECT state, nextTaskId FROM session WHERE id = 's'").use { c ->
                c.moveToFirst()
                assertEquals("paused", c.getString(0))
                assertNull(c.getString(1))
            }
        }
    }

    private companion object {
        const val DB = "migration-test.db"
    }

    @Test
    fun migrate3To4KeepsRowsAndAddsReward() {
        helper.createDatabase(DB, 3).use { db ->
            db.execSQL(
                "INSERT INTO child (id, name, code, ageYears, effectLevel, characterVariant, isEval, createdAt) " +
                    "VALUES ('c', 'n', 'C1', 5, 'calm', 'boy', 0, 0)"
            )
            db.execSQL(
                "INSERT INTO session (id, childId, taskId, kind, state, startedAt, endedAt, catalogVersion, rulesVersion, " +
                    "stepIndex, attemptNumber, promptLevel, nextTaskId) VALUES ('s', 'c', 't', 'assessment', 'done', 0, 0, 'v', '2', 0, 1, 1, NULL)"
            )
            db.execSQL("INSERT INTO correction (id, sessionId, stepId, level, reason, createdAt) VALUES ('k', 's', 'st', 2, NULL, 1)")
        }
        helper.runMigrationsAndValidate(DB, 4, true, AppDatabase.MIGRATION_3_4).use { db ->
            db.query("SELECT taskId FROM session WHERE id = 's'").use { c ->
                c.moveToFirst()
                assertEquals("t", c.getString(0))
            }
            db.query("SELECT level FROM correction WHERE id = 'k'").use { c ->
                c.moveToFirst()
                assertEquals(2, c.getInt(0))
            }
            db.execSQL("INSERT INTO reward (id, childId, sessionId, stars, stickerId, createdAt) VALUES ('r', 'c', 's', 2, NULL, 1)")
            db.execSQL("PRAGMA foreign_keys = ON")
            db.execSQL("DELETE FROM child WHERE id = 'c'")
            db.query("SELECT COUNT(*) FROM reward").use { c ->
                c.moveToFirst()
                assertEquals("rewards cascade with their child", 0, c.getInt(0))
            }
        }
    }

    @Test
    fun migrate4To5KeepsRowsAndAddsTrainingResult() {
        helper.createDatabase(DB, 4).use { db ->
            db.execSQL(
                "INSERT INTO child (id, name, code, ageYears, effectLevel, characterVariant, isEval, createdAt) " +
                    "VALUES ('c', 'n', 'C1', 5, 'calm', 'boy', 0, 0)"
            )
            db.execSQL("INSERT INTO reward (id, childId, sessionId, stars, stickerId, createdAt) VALUES ('r', 'c', NULL, 2, NULL, 1)")
        }
        helper.runMigrationsAndValidate(DB, 5, true, AppDatabase.MIGRATION_4_5).use { db ->
            db.query("SELECT stars FROM reward WHERE id = 'r'").use { c ->
                c.moveToFirst()
                assertEquals(2, c.getInt(0))
            }
            db.execSQL(
                "INSERT INTO training_result (id, childId, activityId, count, durationSec, criterionMet, createdAt) " +
                    "VALUES ('t', 'c', 'a', NULL, 30, 0, 1)"
            )
            db.execSQL("PRAGMA foreign_keys = ON")
            db.execSQL("DELETE FROM child WHERE id = 'c'")
            db.query("SELECT COUNT(*) FROM training_result").use { c ->
                c.moveToFirst()
                assertEquals("training results cascade with their child", 0, c.getInt(0))
            }
        }
    }

    @Test
    fun migrate5To6DedupesCorrectionsKeepingLatestAndMakesThemUnique() {
        helper.createDatabase(DB, 5).use { db ->
            db.execSQL(
                "INSERT INTO child (id, name, code, ageYears, effectLevel, characterVariant, isEval, createdAt) " +
                    "VALUES ('c', 'n', 'C1', 5, 'calm', 'boy', 0, 0)"
            )
            db.execSQL(
                "INSERT INTO session (id, childId, taskId, kind, state, startedAt, endedAt, catalogVersion, rulesVersion, " +
                    "stepIndex, attemptNumber, promptLevel, nextTaskId) VALUES ('s', 'c', 't', 'assessment', 'done', 0, 0, 'v', '3', 0, 1, 1, NULL)"
            )
            db.execSQL("INSERT INTO correction (id, sessionId, stepId, level, reason, createdAt) VALUES ('k1', 's', 'st', 1, NULL, 1)")
            db.execSQL("INSERT INTO correction (id, sessionId, stepId, level, reason, createdAt) VALUES ('k2', 's', 'st', 3, NULL, 5)")
            db.execSQL("INSERT INTO correction (id, sessionId, stepId, level, reason, createdAt) VALUES ('k3', 's', 'st', 2, NULL, 5)")
            db.execSQL(
                "INSERT INTO correction (id, sessionId, stepId, level, reason, createdAt) VALUES ('k4', 's', 'other', NULL, NULL, 2)"
            )
        }
        helper.runMigrationsAndValidate(DB, 6, true, AppDatabase.MIGRATION_5_6).use { db ->
            db.query("SELECT id FROM correction ORDER BY stepId").use { c ->
                val ids = mutableListOf<String>()
                while (c.moveToNext()) ids += c.getString(0)
                assertEquals("latest per step kept (same createdAt: last inserted)", listOf("k4", "k3"), ids)
            }
            db.execSQL(
                "INSERT OR REPLACE INTO correction (id, sessionId, stepId, level, reason, createdAt) VALUES ('k5', 's', 'st', 1, NULL, 9)"
            )
            db.query("SELECT COUNT(*) FROM correction WHERE stepId = 'st'").use { c ->
                c.moveToFirst()
                assertEquals("unique (sessionId, stepId)", 1, c.getInt(0))
            }
        }
    }

    @Test
    fun migrate6To7KeepsRowsAndAddsBlindRating() {
        helper.createDatabase(DB, 6).use { db ->
            db.execSQL(
                "INSERT INTO child (id, name, code, ageYears, effectLevel, characterVariant, isEval, createdAt) " +
                    "VALUES ('c', 'n', 'C1', 5, 'calm', 'boy', 0, 0)"
            )
            db.execSQL(
                "INSERT INTO session (id, childId, taskId, kind, state, startedAt, endedAt, catalogVersion, rulesVersion, " +
                    "stepIndex, attemptNumber, promptLevel, nextTaskId) VALUES ('s', 'c', 't', 'assessment', 'done', 0, 0, 'v', '3', 0, 1, 1, NULL)"
            )
            db.execSQL(
                "INSERT INTO attempt (id, sessionId, stepId, number, promptLevel, helpPressed, verdict, reason, verifier, modelId, " +
                    "promptVersion, latencyMs, framePaths, clipFile, createdAt, modelReason) " +
                    "VALUES ('a', 's', 'st', 1, 1, 0, 'done', 'r', 'pose', NULL, NULL, 0, '[]', NULL, 1, NULL)"
            )
        }
        helper.runMigrationsAndValidate(DB, 7, true, AppDatabase.MIGRATION_6_7).use { db ->
            db.query("SELECT verdict FROM attempt WHERE id = 'a'").use { c ->
                c.moveToFirst()
                assertEquals("done", c.getString(0))
            }
            db.execSQL("INSERT INTO blind_rating (id, attemptId, verdict, createdAt) VALUES ('b1', 'a', 'not_done', 1)")
            db.execSQL("INSERT OR REPLACE INTO blind_rating (id, attemptId, verdict, createdAt) VALUES ('b2', 'a', 'cannot_tell', 2)")
            db.query("SELECT id, verdict FROM blind_rating").use { c ->
                assertEquals("one row per attempt", 1, c.count)
                c.moveToFirst()
                assertEquals("cannot_tell", c.getString(1))
            }
            db.execSQL("PRAGMA foreign_keys = ON")
            db.execSQL("DELETE FROM attempt WHERE id = 'a'")
            db.query("SELECT COUNT(*) FROM blind_rating").use { c ->
                c.moveToFirst()
                assertEquals("blind ratings cascade with their attempt", 0, c.getInt(0))
            }
        }
    }
}
