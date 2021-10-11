package com.simplecityapps.shuttle.mediaimport.model

import com.simplecityapps.shuttle.inject.Inject
import com.simplecityapps.shuttle.mediaimport.MediaProvider
import com.simplecityapps.shuttle.mediaimport.MediaProviderFactory
import com.simplecityapps.shuttle.preferences.GeneralPreferenceManager
import kotlinx.coroutines.flow.first

class GetUserSelectedMediaProviders @Inject constructor(
    private val mediaProviderFactory: MediaProviderFactory,
    private val preferenceManager: GeneralPreferenceManager
) {
    suspend operator fun invoke(): List<MediaProvider> {
        return mediaProviderFactory.getMediaProviders(
            preferenceManager.getMediaProviders().first() ?: emptyList()
        )
    }
}