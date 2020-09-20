package com.simplecityapps.shuttle.appinitializers

import android.app.Application
import com.simplecityappds.saf.SafDirectoryHelper
import com.simplecityapps.localmediaprovider.local.provider.mediastore.MediaStoreSongProvider
import com.simplecityapps.localmediaprovider.local.provider.taglib.TaglibSongProvider
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
    private val taglibSongProvider: TaglibSongProvider,
    private val mediaStoreSongProvider: MediaStoreSongProvider,
    @Named("AppCoroutineScope") private val appCoroutineScope: CoroutineScope
) : AppInitializer {

    override fun init(application: Application) {

        when (playbackPreferenceManager.songProvider) {
            PlaybackPreferenceManager.SongProvider.TagLib -> {
                mediaImporter.mediaProvider = taglibSongProvider
                appCoroutineScope.launch {
                    delay(2000)
                    withContext(Dispatchers.IO) {
                        application.contentResolver?.persistedUriPermissions
                            ?.filter { uriPermission -> uriPermission.isReadPermission || uriPermission.isWritePermission }
                            ?.mapNotNull { uriPermission ->
                                SafDirectoryHelper.buildFolderNodeTree(application.contentResolver, uriPermission.uri).distinctUntilChanged()
                            }
                            ?.merge()
                            ?.toList()
                            ?.let { directories ->
                                taglibSongProvider.directories = directories
                                if (directories.isNotEmpty()) {
                                    mediaImporter.import()
                                }
                            }
                    }
                }
            }
            PlaybackPreferenceManager.SongProvider.MediaStore -> {
                mediaImporter.mediaProvider = mediaStoreSongProvider
                appCoroutineScope.launch {
                    delay(2000)
                    mediaImporter.import()
                }
            }
        }
    }
}