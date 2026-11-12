package com.aitherapist.dls.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/** Encrypted app database (AD-8). Schema changes need an explicit Migration; destructive migration is forbidden. */
@Database(entities = [ChildEntity::class, SessionEntity::class, AttemptEntity::class], version = 1, exportSchema = true)
@TypeConverters(Converters::class)
internal abstract class AppDatabase : RoomDatabase() {
    abstract fun childDao(): ChildDao

    abstract fun sessionDao(): SessionDao

    abstract fun attemptDao(): AttemptDao

    companion object {
        const val NAME = "dls.db"

        /** Opens (or creates) the SQLCipher database [name] keyed with [passphrase]. */
        fun build(context: Context, passphrase: ByteArray, name: String = NAME): AppDatabase {
            System.loadLibrary("sqlcipher")
            return Room
                .databaseBuilder(context.applicationContext, AppDatabase::class.java, name)
                .openHelperFactory(SupportOpenHelperFactory(passphrase))
                .build()
        }
    }
}
