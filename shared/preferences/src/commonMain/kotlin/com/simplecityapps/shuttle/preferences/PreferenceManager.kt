package com.simplecityapps.shuttle.preferences

import kotlinx.coroutines.flow.Flow

interface PreferenceManager {
    suspend fun setBoolean(key: String, value: Boolean)
    fun getBoolean(key: String): Flow<Boolean>
}