package com.simplecityapps.shuttle.mediaprovider.factory

import com.simplecityapps.shuttle.mediaprovider.common.MediaProvider
import com.simplecityapps.shuttle.model.MediaProviderType

interface MediaProviderFactory {
    fun getMediaProviders(mediaProviderTypes: List<MediaProviderType>): List<MediaProvider>
}