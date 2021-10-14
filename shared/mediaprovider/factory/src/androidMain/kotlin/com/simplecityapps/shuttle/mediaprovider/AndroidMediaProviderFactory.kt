package com.simplecityapps.shuttle.mediaprovider

import com.simplecityapps.shuttle.common.mediaprovider.mediastore.MediaStoreMediaProvider
import com.simplecityapps.shuttle.mediaprovider.common.MediaProvider
import com.simplecityapps.shuttle.mediaprovider.common.MediaProviderFactory
import com.simplecityapps.shuttle.mediaprovider.jellyfin.JellyfinMediaProvider
import com.simplecityapps.shuttle.model.MediaProviderType

class AndroidMediaProviderFactory(
    val mediaStoreMediaProvider: MediaStoreMediaProvider,
    val jellyfinMediaProvider: JellyfinMediaProvider
) : MediaProviderFactory {

    override fun getMediaProviders(mediaProviderTypes: List<MediaProviderType>): List<MediaProvider> {
        return mediaProviderTypes.map { mediaProviderType ->
            when (mediaProviderType) {
                MediaProviderType.MediaStore -> {
                    mediaStoreMediaProvider
                }
                MediaProviderType.Shuttle -> TODO()
                MediaProviderType.Emby -> TODO()
                MediaProviderType.Jellyfin -> {
                    jellyfinMediaProvider

                }
                MediaProviderType.Plex -> TODO()
            }
        }
    }
}