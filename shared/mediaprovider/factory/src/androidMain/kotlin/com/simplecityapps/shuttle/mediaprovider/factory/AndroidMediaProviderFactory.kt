package com.simplecityapps.shuttle.mediaprovider.factory

import com.simplecityapps.shuttle.common.mediaprovider.mediastore.MediaStoreMediaProvider
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.mediaprovider.common.MediaProvider
import com.simplecityapps.shuttle.mediaprovider.emby.EmbyMediaProvider
import com.simplecityapps.shuttle.mediaprovider.jellyfin.JellyfinMediaProvider

class AndroidMediaProviderFactory(
    val mediaStoreMediaProvider: MediaStoreMediaProvider,
    val jellyfinMediaProvider: JellyfinMediaProvider,
    val embyMediaProvider: EmbyMediaProvider,
) : MediaProviderFactory {

    override fun getMediaProviders(mediaProviderTypes: List<MediaProviderType>): List<MediaProvider> {
        return mediaProviderTypes.map { mediaProviderType ->
            when (mediaProviderType) {
                MediaProviderType.MediaStore -> mediaStoreMediaProvider
                MediaProviderType.Emby -> embyMediaProvider
                MediaProviderType.Jellyfin -> jellyfinMediaProvider
                MediaProviderType.Shuttle -> TODO()
                MediaProviderType.Plex -> TODO()
            }
        }
    }
}