package com.aitherapist.dls.data

import android.content.Context
import com.aitherapist.dls.catalog.AssetPresence
import com.aitherapist.dls.catalog.CachedAssetPresence
import com.aitherapist.dls.catalog.CachedCatalogRepository
import com.aitherapist.dls.catalog.CatalogRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/** Shell side of the catalogue: reads the generated files from the APK assets (Story 2.8). */
@Singleton
class AssetCatalogRepository
@Inject
constructor(@ApplicationContext context: Context) :
    CatalogRepository by CachedCatalogRepository({ path -> context.assets.open(path).bufferedReader().use { it.readText() } })

@Module
@InstallIn(SingletonComponent::class)
internal abstract class CatalogModule {
    @Binds
    abstract fun catalogRepository(impl: AssetCatalogRepository): CatalogRepository

    companion object {
        /** One cached asset-existence checker for the app (task readiness: step clips present). */
        @Provides
        @Singleton
        fun assetPresence(@ApplicationContext context: Context): AssetPresence =
            CachedAssetPresence { dir -> context.assets.list(dir)?.toList() }
    }
}
