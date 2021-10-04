package com.simplecityapps.shuttle.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DataStorePreferenceManager(
    private val dataStore: DataStore<Preferences>
) : PreferenceManager {

    override suspend fun setBoolean(key: String, value: Boolean) {
        dataStore.edit { settings ->
            settings[booleanPreferencesKey(key)] = value
        }
    }

    override fun getBoolean(key: String): Flow<Boolean> {
        return dataStore.data.map { settings -> settings[booleanPreferencesKey(key)] ?: false }
    }
}