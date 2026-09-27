package com.simplecityapps.shuttle.persistence

/**
 * Secrets under string keys (media server credentials, the client id): EncryptedSharedPreferences on Android
 * ([SharedPreferencesSecureStore]), the Keychain on iOS, [InMemoryKeyValueStore] in tests.
 */
interface SecureStore {
    fun getString(key: String): String?

    /** Stores [value] under [key]; null removes it. */
    fun putString(
        key: String,
        value: String?
    )

    /** False when nothing is stored under [key]. */
    fun getBoolean(key: String): Boolean

    fun putBoolean(
        key: String,
        value: Boolean
    )
}
