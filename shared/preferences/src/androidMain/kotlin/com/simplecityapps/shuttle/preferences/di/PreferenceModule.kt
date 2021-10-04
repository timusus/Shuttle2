package com.simplecityapps.shuttle.preferences.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.simplecityapps.shuttle.preferences.DataStorePreferenceManager
import com.simplecityapps.shuttle.preferences.GeneralPreferenceManager
import com.simplecityapps.shuttle.preferences.PreferenceManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
class PreferencesModule {

    @Provides
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        ) {
            context.preferencesDataStoreFile("settings")
        }
    }

    @Provides
    fun providePreferenceManager(dataStore: DataStore<Preferences>): PreferenceManager {
        return DataStorePreferenceManager(dataStore)
    }

    @Provides
    fun provideGeneralPreferenceManager(preferenceManager: PreferenceManager): GeneralPreferenceManager {
        return GeneralPreferenceManager(preferenceManager)
    }
}