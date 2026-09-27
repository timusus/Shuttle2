package com.simplecityapps.shuttle.persistence

class SecurePreferenceManager(private val store: SecureStore) {
    // Media server credentials, keyed by ServerCredentialStore (`<server>_*`)

    fun getString(key: String): String? = store.getString(key)

    /** Stores [value] under [key]; null removes it. */
    fun putString(
        key: String,
        value: String?
    ) {
        store.putString(key, value)
    }

    fun getBoolean(key: String): Boolean = store.getBoolean(key)

    fun putBoolean(
        key: String,
        value: Boolean
    ) {
        store.putBoolean(key, value)
    }

    // Client identity

    var clientId: String?
        set(value) {
            store.putString("client_id", value)
        }
        get() {
            return store.getString("client_id")
        }
}
