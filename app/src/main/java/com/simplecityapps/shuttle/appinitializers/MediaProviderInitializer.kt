package com.simplecityapps.shuttle.appinitializers

import android.app.Application
import com.simplecityapps.localmediaprovider.local.provider.mediastore.MediaStoreMediaProvider
import com.simplecityapps.localmediaprovider.local.provider.taglib.TaglibMediaProvider
import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.mediaprovider.MediaProvider
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.provider.emby.EmbyMediaProvider
import com.simplecityapps.provider.jellyfin.JellyfinMediaProvider
import com.simplecityapps.saf.SafDirectoryHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Named

class MediaProviderInitializer @Inject constructor(
    private val mediaImporter: MediaImporter,
    private val playbackPreferenceManager: PlaybackPreferenceManager,
    private val taglibMediaProvider: TaglibMediaProvider,
    private val mediaStoreMediaProvider: MediaStoreMediaProvider,
    private val embyMediaProvider: EmbyMediaProvider,
    private val jellyfinMediaProvider: JellyfinMediaProvider,
    @Named("AppCoroutineScope") private val appCoroutineScope: CoroutineScope
) : AppInitializer {

    override fun init(application: Application) {
        playbackPreferenceManager.mediaProviderTypes.forEach { type ->
            when (type) {
                MediaProvider.Type.Shuttle -> {
                    mediaImporter.mediaProviders += taglibMediaProvider
                    appCoroutineScope.launch {
                        withContext(Dispatchers.IO) {
                            application.contentResolver?.persistedUriPermissions
                                ?.filter { uriPermission -> uriPermission.isReadPermission || uriPermission.isWritePermission }
                                ?.map { uriPermission -> SafDirectoryHelper.buildFolderNodeTree(application.contentResolver, uriPermission.uri).distinctUntilChanged() }
                                ?.merge()
                                ?.toList()
                                ?.let { directories ->
                                    taglibMediaProvider.directories = directories
                                }
                        }
                    }
                }
                MediaProvider.Type.MediaStore -> {
                    mediaImporter.mediaProviders += mediaStoreMediaProvider
                }
                MediaProvider.Type.Emby -> {
                    mediaImporter.mediaProviders += embyMediaProvider
                }
                MediaProvider.Type.Jellyfin-> {
                    mediaImporter.mediaProviders += jellyfinMediaProvider
                }
            }
        }
    }
}