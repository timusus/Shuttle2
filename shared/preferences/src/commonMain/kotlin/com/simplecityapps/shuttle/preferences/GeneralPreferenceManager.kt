package com.simplecityapps.shuttle.preferences

import kotlinx.coroutines.flow.Flow

class GeneralPreferenceManager(private val preferenceManager: PreferenceManager) {

    object PreferenceKey {
        const val HAS_ONBOARDED = "has_onboarded"
    }

    suspend fun setHasOnboarded(value: Boolean) {
        preferenceManager.setBoolean(PreferenceKey.HAS_ONBOARDED, value)
    }

    fun getHasOnboarded(): Flow<Boolean> {
        return preferenceManager.getBoolean(PreferenceKey.HAS_ONBOARDED)
    }
}