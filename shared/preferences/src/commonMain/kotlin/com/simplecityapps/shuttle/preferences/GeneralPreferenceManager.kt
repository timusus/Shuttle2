package com.simplecityapps.shuttle.preferences

import com.simplecityapps.shuttle.model.MediaProviderType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GeneralPreferenceManager(private val preferenceManager: PreferenceManager) {

    object PreferenceKey {
        const val HAS_ONBOARDED = "has_onboarded"
        const val MEDIA_PROVIDERS = "media_providers"
    }

    suspend fun setHasOnboarded(value: Boolean) {
        preferenceManager.setBoolean(PreferenceKey.HAS_ONBOARDED, value)
    }

    fun getHasOnboarded(): Flow<Boolean> {
        return preferenceManager.getBoolean(PreferenceKey.HAS_ONBOARDED)
    }

    suspend fun setMediaProviders(mediaProviders: List<MediaProviderType>) {
        preferenceManager.setString(PreferenceKey.MEDIA_PROVIDERS, mediaProviders.map { it.ordinal }.joinToString(","))
    }

    fun getMediaProviders(): Flow<List<MediaProviderType>?> {
        return preferenceManager.getString(PreferenceKey.MEDIA_PROVIDERS)
            .map { string ->
                string?.split(",")
                    ?.filter { it.isNotEmpty() }
                    ?.map {
                        MediaProviderType.init(it.toInt())
                    }
            }
    }
}