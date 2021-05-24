package com.simplecityapps.shuttle.appinitializers

import android.app.Application
import com.simplecityapps.localmediaprovider.local.provider.mediastore.MediaStoreMediaProvider
import com.simplecityapps.localmediaprovider.local.provider.taglib.TaglibMediaProvider
import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.mediaprovider.MediaProvider
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.provider.emby.EmbyMediaProvider
import com.simplecityapps.provider.jellyfin.JellyfinMediaProvider
import com.simplecityapps.provider.plex.PlexMediaProvider
import javax.inject.Inject

class MediaProviderInitializer @Inject constructor(
    private val mediaImporter: MediaImporter,
    private val playbackPreferenceManager: PlaybackPreferenceManager,
    private val taglibMediaProvider: TaglibMediaProvider,
    private val mediaStoreMediaProvider: MediaStoreMediaProvider,
    private val embyMediaProvider: EmbyMediaProvider,
    private val jellyfinMediaProvider: JellyfinMediaProvider,
    private val plexMediaProvider: PlexMediaProvider
) : AppInitializer {

    override fun init(application: Application) {
        playbackPreferenceManager.mediaProviderTypes.forEach { type ->
            when (type) {
                MediaProvider.Type.Shuttle -> mediaImporter.mediaProviders += taglibMediaProvider
                MediaProvider.Type.MediaStore -> mediaImporter.mediaProviders += mediaStoreMediaProvider
                MediaProvider.Type.Emby -> mediaImporter.mediaProviders += embyMediaProvider
                MediaProvider.Type.Jellyfin -> mediaImporter.mediaProviders += jellyfinMediaProvider
                MediaProvider.Type.Plex -> mediaImporter.mediaProviders += plexMediaProvider
            }
        }
    }
}