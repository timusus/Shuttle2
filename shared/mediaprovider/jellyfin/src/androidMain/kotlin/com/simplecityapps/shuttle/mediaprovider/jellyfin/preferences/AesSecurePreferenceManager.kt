package com.simplecityapps.shuttle.mediaprovider.jellyfin.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.simplecityapps.shuttle.preferences.SecurePreferenceManager
import com.simplecityapps.shuttle.security.SecurityManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AesSecurePreferenceManager(
    private val dataStore: DataStore<Preferences>,
    private val securityManager: SecurityManager
) : SecurePreferenceManager {

    override suspend fun setBoolean(key: String, value: Boolean) {
        dataStore.edit { settings ->
            settings[booleanPreferencesKey(key)] = value
        }
    }

    override fun getBoolean(key: String): Flow<Boolean> {
        return dataStore.data.map { settings -> settings[booleanPreferencesKey(key)] ?: false }
    }

    override suspend fun setString(key: String, value: String?) {
        dataStore.edit { settings ->
            val preferenceKey = stringPreferencesKey(key)
            if (value == null) {
                settings.remove(preferenceKey)
            } else {
                settings[preferenceKey] = securityManager.encryptData(value.toByteArray(Charsets.UTF_8))
            }
        }
    }

    override fun getString(key: String, default: String?): Flow<String?> {
        return dataStore.data.map { settings ->
            settings[stringPreferencesKey(key)]?.let {
                securityManager.decryptData(it).toString(Charsets.UTF_8)
            } ?: default
        }
    }
}