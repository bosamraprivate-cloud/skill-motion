package com.aitherapist.dls.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object DataModule {
    private const val SETTINGS_FILE = "secure_settings"

    @Provides
    @Singleton
    fun dataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile(SETTINGS_FILE) }

    @Provides
    @Singleton
    fun secureSettings(store: DataStore<Preferences>, keys: KeyManager): SecureSettings = SecureSettings(store, keys)

    @Provides
    fun therapistSettings(settings: SecureSettings): TherapistSettings = settings

    @Provides
    @Singleton
    fun frameStore(@ApplicationContext context: Context, keys: KeyManager): FrameStore = FrameStore(context.filesDir, keys)

    /** The database opens lazily on the IO dispatcher when a repository first awaits it. */
    @Provides
    @Singleton
    fun databaseHolder(@ApplicationContext context: Context, settings: SecureSettings): DatabaseHolder = DatabaseHolder(context, settings)
}
