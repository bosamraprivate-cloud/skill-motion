package com.aitherapist.dls.data

import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM check of the v1 -> v2 migration; the on-device MigrationTestHelper test is in androidTest. */
class MigrationSqlTest {
    @Test
    fun `migration 1 to 2 only adds the nullable modelReason column`() {
        val executed = mutableListOf<String>()
        val db =
            Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SupportSQLiteDatabase::class.java)) { _, method, args ->
                if (method.name == "execSQL") executed += args!![0] as String
                null
            } as SupportSQLiteDatabase
        AppDatabase.MIGRATION_1_2.migrate(db)
        assertEquals(listOf("ALTER TABLE `attempt` ADD COLUMN `modelReason` TEXT"), executed)
        assertEquals(1, AppDatabase.MIGRATION_1_2.startVersion)
        assertEquals(2, AppDatabase.MIGRATION_1_2.endVersion)
    }

    @Test
    fun `exported v2 schema has the modelReason column`() {
        val schema = File("schemas/com.aitherapist.dls.data.AppDatabase/2.json")
        assertTrue(schema.path, schema.exists())
        assertTrue(schema.readText().contains("\"columnName\": \"modelReason\""))
    }

    @Test
    fun `migration 4 to 5 matches the exported v5 training_result schema`() {
        val schema = File("schemas/com.aitherapist.dls.data.AppDatabase/5.json").readText()
        AppDatabase.SQL_4_5.forEach { sql ->
            val exported = sql.replace("`training_result`", "`\${TABLE_NAME}`")
            assertTrue(sql, schema.contains(exported))
        }
        assertEquals(4, AppDatabase.MIGRATION_4_5.startVersion)
        assertEquals(5, AppDatabase.MIGRATION_4_5.endVersion)
    }

    @Test
    fun `migration 5 to 6 makes the exported correction index unique`() {
        val schema = File("schemas/com.aitherapist.dls.data.AppDatabase/6.json").readText()
        val create = AppDatabase.SQL_5_6.last()
        assertTrue(create, create.startsWith("CREATE UNIQUE INDEX"))
        assertTrue(
            schema.contains(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_correction_sessionId_stepId` ON `\${TABLE_NAME}` (`sessionId`, `stepId`)"
            )
        )
        assertEquals(5, AppDatabase.MIGRATION_5_6.startVersion)
        assertEquals(6, AppDatabase.MIGRATION_5_6.endVersion)
    }

    @Test
    fun `migration 6 to 7 matches the exported v7 blind_rating schema`() {
        val schema = File("schemas/com.aitherapist.dls.data.AppDatabase/7.json").readText()
        AppDatabase.SQL_6_7.forEach { sql ->
            val exported = sql.replace("`blind_rating` (", "`\${TABLE_NAME}` (")
            assertTrue(sql, schema.contains(exported))
        }
        assertEquals(6, AppDatabase.MIGRATION_6_7.startVersion)
        assertEquals(7, AppDatabase.MIGRATION_6_7.endVersion)
    }

    @Test
    fun `migration 7 to 8 adds the exported v8 showTimeRing column`() {
        val schema = File("schemas/com.aitherapist.dls.data.AppDatabase/8.json").readText()
        assertTrue(schema.contains("`showTimeRing` INTEGER NOT NULL DEFAULT 1"))
        assertEquals(listOf("ALTER TABLE `child` ADD COLUMN `showTimeRing` INTEGER NOT NULL DEFAULT 1"), AppDatabase.SQL_7_8)
        assertEquals(7, AppDatabase.MIGRATION_7_8.startVersion)
        assertEquals(8, AppDatabase.MIGRATION_7_8.endVersion)
        assertTrue(AppDatabase.MIGRATIONS.contains(AppDatabase.MIGRATION_7_8))
    }
}
