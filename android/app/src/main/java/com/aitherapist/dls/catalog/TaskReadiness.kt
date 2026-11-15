package com.aitherapist.dls.catalog

import java.util.concurrent.ConcurrentHashMap

/** Answers whether an asset file exists in the APK. */
fun interface AssetPresence {
    fun exists(path: String): Boolean
}

/**
 * [AssetPresence] backed by a directory lister (AssetManager.list in the app, a fake in tests). Each directory is
 * listed once and cached, so checking every step clip costs one listing per variant folder.
 */
class CachedAssetPresence(private val list: (dir: String) -> List<String>?) : AssetPresence {
    private val cache = ConcurrentHashMap<String, Set<String>>()

    override fun exists(path: String): Boolean {
        val dir = path.substringBeforeLast('/', missingDelimiterValue = "")
        val name = path.substringAfterLast('/')
        return name in cache.getOrPut(dir) { list(dir)?.toSet().orEmpty() }
    }
}

/**
 * A task is ready for a character variant when every one of its steps has that variant's demonstration clip and the
 * clip file is in the APK. Paths come only from the catalogue's step media; no task or step id is named here.
 */
object TaskReadiness {
    fun isReady(task: CatalogTask, variant: String, assets: AssetPresence): Boolean =
        task.steps.isNotEmpty() && task.steps.all { step -> step.media(variant)?.clip?.let(assets::exists) == true }

    /** The variants (from the catalogue's media keys) for which [task] is ready. */
    fun readyVariants(task: CatalogTask, assets: AssetPresence): Set<String> =
        task.steps.flatMap { it.media.keys }.toSet().filterTo(mutableSetOf()) { isReady(task, it, assets) }
}
