package com.aitherapist.dls.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Opens the encrypted database lazily on [Dispatchers.IO] the first time [get] is awaited (no main-thread I/O). */
internal class DatabaseHolder(private val open: suspend () -> AppDatabase) {
    constructor(context: Context, settings: SecureSettings) :
        this({ AppDatabase.build(context, settings.databasePassphrase()) })

    private val lock = Mutex()

    @Volatile private var db: AppDatabase? = null

    suspend fun get(): AppDatabase = db ?: lock.withLock { db ?: withContext(Dispatchers.IO) { open() }.also { db = it } }
}
