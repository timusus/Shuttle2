package com.simplecityapps.shuttle.preferences

import kotlinx.coroutines.flow.Flow

interface PreferenceManager {
    suspend fun setBoolean(key: String, value: Boolean)
    fun getBoolean(key: String): Flow<Boolean>

    suspend fun setString(key: String, value: String)
    fun getString(key: String, default: String? = null): Flow<String?>
}