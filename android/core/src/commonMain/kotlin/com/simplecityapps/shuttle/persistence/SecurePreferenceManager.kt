package com.simplecityapps.shuttle.persistence

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@SingleIn(AppScope::class)
class SecurePreferenceManager @Inject constructor(private val store: SecureStore) {
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
