package com.simplecityapps.shuttle.appinitializers

import android.app.Application
import android.content.Context
import com.simplecityapps.localmediaprovider.local.provider.mediastore.MediaStoreMediaProvider
import com.simplecityapps.localmediaprovider.local.provider.taglib.TaglibMediaProvider
import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.mediaprovider.worker.ImportFrequency
import com.simplecityapps.mediaprovider.worker.MediaImportWorker
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.provider.emby.EmbyMediaProvider
import com.simplecityapps.provider.jellyfin.JellyfinMediaProvider
import com.simplecityapps.provider.plex.PlexMediaProvider
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class MediaProviderInitializer
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val mediaImporter: MediaImporter,
    private val preferenceManager: GeneralPreferenceManager,
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
                MediaProviderType.Shuttle -> mediaImporter.mediaProviders += taglibMediaProvider
                MediaProviderType.MediaStore -> mediaImporter.mediaProviders += mediaStoreMediaProvider
                MediaProviderType.Emby -> mediaImporter.mediaProviders += embyMediaProvider
                MediaProviderType.Jellyfin -> mediaImporter.mediaProviders += jellyfinMediaProvider
                MediaProviderType.Plex -> mediaImporter.mediaProviders += plexMediaProvider
            }
        }

        MediaImportWorker.updateWork(
            context = context,
            importFrequency = ImportFrequency.values().first { it.value == preferenceManager.mediaImportFrequency }
        )
    }
}
