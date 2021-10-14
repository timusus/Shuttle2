package com.simplecityapps.shuttle.mediaprovider.common

import com.simplecityapps.shuttle.model.MediaProviderType

interface MediaProviderFactory {
    fun getMediaProviders(mediaProviderTypes: List<MediaProviderType>): List<MediaProvider>
}