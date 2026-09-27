package com.simplecityapps.shuttle.persistence

import android.content.SharedPreferences

class SecurePreferenceManager(private val sharedPreferences: SharedPreferences) {
    // Media server credentials, keyed by ServerCredentialStore (`<server>_*`)

    fun getString(key: String): String? = sharedPreferences.getString(key, null)

    /** Stores [value] under [key]; null removes it. */
    fun putString(
        key: String,
        value: String?
    ) {
        sharedPreferences.put(key, value)
    }

    fun getBoolean(key: String): Boolean = sharedPreferences.getBoolean(key, false)

    fun putBoolean(
        key: String,
        value: Boolean
    ) {
        sharedPreferences.put(key, value)
    }

    // Client identity

    var clientId: String?
        set(value) {
            sharedPreferences.put("client_id", value)
        }
        get() {
            return sharedPreferences.getString("client_id", null)
        }
}
