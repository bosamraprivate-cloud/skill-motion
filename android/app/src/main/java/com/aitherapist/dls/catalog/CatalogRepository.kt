package com.aitherapist.dls.catalog

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Read access to the generated catalogue (Story 2.8). Loaded once, cached; never a built-in fallback. */
interface CatalogRepository {
    /** The catalogue, or a failure whose message names the bad file. */
    suspend fun load(): Result<Catalog>
}

/** Loads catalogue text off the main thread and caches the decoded result; failures are not cached. */
class CachedCatalogRepository(private val read: (String) -> String, private val io: CoroutineDispatcher = Dispatchers.IO) :
    CatalogRepository {
    private val lock = Mutex()

    @Volatile private var cached: Catalog? = null

    override suspend fun load(): Result<Catalog> = cached?.let { Result.success(it) } ?: lock.withLock {
        cached?.let { return@withLock Result.success(it) }
        withContext(io) { runCatching { CatalogDecoder.decode(read) } }.onSuccess { cached = it }
    }
}
