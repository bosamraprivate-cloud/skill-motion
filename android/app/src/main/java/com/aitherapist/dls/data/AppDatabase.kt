package com.aitherapist.dls.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/** Encrypted app database (AD-8). Schema changes need an explicit Migration; destructive migration is forbidden. */
@Database(
    entities = [ChildEntity::class, SessionEntity::class, AttemptEntity::class, CorrectionEntity::class],
    version = 3,
    exportSchema = true
)
@TypeConverters(Converters::class)
internal abstract class AppDatabase : RoomDatabase() {
    abstract fun childDao(): ChildDao

    abstract fun sessionDao(): SessionDao

    abstract fun attemptDao(): AttemptDao

    abstract fun correctionDao(): CorrectionDao

    companion object {
        const val NAME = "dls.db"

        /** v1 -> v2 (Story 3.6): nullable `attempt.modelReason`; existing rows keep their reason. */
        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(SQL_1_2)
                }
            }

        const val SQL_1_2 = "ALTER TABLE `attempt` ADD COLUMN `modelReason` TEXT"

        /** v2 -> v3 (Story 4.1): `correction` table and nullable `session.nextTaskId`; existing rows are kept. */
        val MIGRATION_2_3 =
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    SQL_2_3.forEach(db::execSQL)
                }
            }

        val SQL_2_3 =
            listOf(
                "ALTER TABLE `session` ADD COLUMN `nextTaskId` TEXT",
                "CREATE TABLE IF NOT EXISTS `correction` (`id` TEXT NOT NULL, `sessionId` TEXT NOT NULL, `stepId` TEXT NOT NULL, " +
                    "`level` INTEGER, `reason` TEXT, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
                    "FOREIGN KEY(`sessionId`) REFERENCES `session`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                "CREATE INDEX IF NOT EXISTS `index_correction_sessionId_stepId` ON `correction` (`sessionId`, `stepId`)"
            )

        val MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3)

        /** Opens (or creates) the SQLCipher database [name] keyed with [passphrase]. */
        fun build(context: Context, passphrase: ByteArray, name: String = NAME): AppDatabase {
            System.loadLibrary("sqlcipher")
            return Room
                .databaseBuilder(context.applicationContext, AppDatabase::class.java, name)
                .openHelperFactory(SupportOpenHelperFactory(passphrase))
                .addMigrations(*MIGRATIONS)
                .build()
        }
    }
}
