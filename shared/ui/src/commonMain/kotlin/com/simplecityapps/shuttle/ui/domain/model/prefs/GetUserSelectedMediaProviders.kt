package com.simplecityapps.shuttle.ui.domain.model.prefs

import com.simplecityapps.shuttle.inject.Inject
import com.simplecityapps.shuttle.mediaprovider.common.MediaProvider
import com.simplecityapps.shuttle.mediaprovider.common.MediaProviderFactory
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