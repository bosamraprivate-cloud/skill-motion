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
@Database(entities = [ChildEntity::class, SessionEntity::class, AttemptEntity::class], version = 2, exportSchema = true)
@TypeConverters(Converters::class)
internal abstract class AppDatabase : RoomDatabase() {
    abstract fun childDao(): ChildDao

    abstract fun sessionDao(): SessionDao

    abstract fun attemptDao(): AttemptDao

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

        val MIGRATIONS = arrayOf(MIGRATION_1_2)

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
