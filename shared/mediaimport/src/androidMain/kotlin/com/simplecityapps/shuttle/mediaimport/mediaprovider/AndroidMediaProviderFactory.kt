package com.simplecityapps.shuttle.mediaimport.mediaprovider

import android.content.Context
import com.simplecityapps.shuttle.mediaimport.MediaProvider
import com.simplecityapps.shuttle.mediaimport.MediaProviderFactory
import com.simplecityapps.shuttle.model.MediaProviderType

class AndroidMediaProviderFactory(
    val context: Context
) : MediaProviderFactory {

    override fun getMediaProviders(mediaProviderTypes: List<MediaProviderType>): List<MediaProvider> {
        return mediaProviderTypes.map { mediaProviderType ->
            when (mediaProviderType) {
                MediaProviderType.MediaStore -> {
                    MediaStoreMediaProvider(context)
                }
                MediaProviderType.Shuttle -> TODO()
                MediaProviderType.Emby -> TODO()
                MediaProviderType.Jellyfin -> TODO()
                MediaProviderType.Plex -> TODO()
            }
        }
    }
}