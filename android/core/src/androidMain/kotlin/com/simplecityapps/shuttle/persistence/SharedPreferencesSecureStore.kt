package com.simplecityapps.shuttle.persistence

import android.content.SharedPreferences

/** A [SecureStore] over a SharedPreferences file: the app's EncryptedSharedPreferences (see PersistenceModule). */
class SharedPreferencesSecureStore(
    private val sharedPreferences: SharedPreferences
) : SecureStore {
    override fun getString(key: String): String? = sharedPreferences.getString(key, null)

    override fun putString(
        key: String,
        value: String?
    ) {
        sharedPreferences.edit().putString(key, value).apply()
    }

    override fun getBoolean(key: String): Boolean = sharedPreferences.getBoolean(key, false)

    override fun putBoolean(
        key: String,
        value: Boolean
    ) {
        sharedPreferences.edit().putBoolean(key, value).apply()
    }
}

/**
 * A [SecurePreferenceManager] over [sharedPreferences]. Kept for the server modules' tests, which build one over
 * their FakeSharedPreferences; new code passes a SecureStore.
 */
fun SecurePreferenceManager(sharedPreferences: SharedPreferences): SecurePreferenceManager = SecurePreferenceManager(SharedPreferencesSecureStore(sharedPreferences))
