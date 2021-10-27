package com.simplecityapps.shuttle.ui.domain.model.mediaprovider

import com.simplecityapps.shuttle.inject.Inject
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.preferences.GeneralPreferenceManager

data class SetMediaProviders @Inject constructor(
    private val preferenceManager: GeneralPreferenceManager
) {
    suspend operator fun invoke(
        mediaProviders: List<MediaProviderType>
    ) = preferenceManager.setMediaProviders(mediaProviders)
}