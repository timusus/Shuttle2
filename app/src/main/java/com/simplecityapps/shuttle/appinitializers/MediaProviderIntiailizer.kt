package com.simplecityapps.shuttle.appinitializers

import android.app.Application
import com.simplecityappds.saf.SafDirectoryHelper
import com.simplecityapps.localmediaprovider.local.provider.mediastore.MediaStoreMediaProvider
import com.simplecityapps.localmediaprovider.local.provider.taglib.TaglibMediaProvider
import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.toList
import javax.inject.Inject
import javax.inject.Named

class MediaProviderInitializer @Inject constructor(
    private val mediaImporter: MediaImporter,
    private val playbackPreferenceManager: PlaybackPreferenceManager,
    private val taglibMediaProvider: TaglibMediaProvider,
    private val mediaStoreMediaProvider: MediaStoreMediaProvider,
    @Named("AppCoroutineScope") private val appCoroutineScope: CoroutineScope
) : AppInitializer {

    override fun init(application: Application) {
        when (playbackPreferenceManager.songProvider) {
            PlaybackPreferenceManager.SongProvider.TagLib -> {
                mediaImporter.mediaProvider = taglibMediaProvider
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
            PlaybackPreferenceManager.SongProvider.MediaStore -> {
                mediaImporter.mediaProvider = mediaStoreMediaProvider
            }
        }
    }
}