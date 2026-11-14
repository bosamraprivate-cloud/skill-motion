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
}
