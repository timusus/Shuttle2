package com.simplecityapps.shuttle.ui.screens.main

import android.content.Context
import com.simplecityappds.saf.SafDirectoryHelper
import com.simplecityapps.localmediaprovider.local.provider.mediastore.MediaStoreSongProvider
import com.simplecityapps.localmediaprovider.local.provider.taglib.FileScanner
import com.simplecityapps.localmediaprovider.local.provider.taglib.TaglibSongProvider
import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.playback.queue.QueueChangeCallback
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.playback.queue.QueueWatcher
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named

interface MainContract {

    interface View {
        fun toggleSheet(visible: Boolean)
        fun showChangelog()
    }

    interface Presenter {
        suspend fun scanMedia()
    }
}

class MainPresenter @Inject constructor(
    private val context: Context,
    @Named("AppCoroutineScope") private val appCoroutineScope: CoroutineScope,
    private val queueManager: QueueManager,
    private val queueWatcher: QueueWatcher,
    private val mediaImporter: MediaImporter,
    private val playbackPreferenceManager: PlaybackPreferenceManager,
    private val fileScanner: FileScanner,
    private val preferenceManager: GeneralPreferenceManager
) : MainContract.Presenter,
    BasePresenter<MainContract.View>(),
    QueueChangeCallback {

    override fun bindView(view: MainContract.View) {
        super.bindView(view)

        queueWatcher.addCallback(this)

        view.toggleSheet(visible = queueManager.getSize() != 0)

        if (!preferenceManager.hasSeenChangelog && preferenceManager.showChangelogOnLaunch) {
            view.showChangelog()
        }

        // Don't bother scanning for media again if we've already scanned once this session
        if (mediaImporter.importCount < 1) {
            appCoroutineScope.launch {
                scanMedia()
            }
        }
    }

    override fun unbindView() {
        super.unbindView()

        queueWatcher.removeCallback(this)
    }

    override suspend fun scanMedia() {
        when (playbackPreferenceManager.songProvider) {
            PlaybackPreferenceManager.SongProvider.MediaStore -> {
                mediaImporter.import(MediaStoreSongProvider(context.applicationContext))
            }
            PlaybackPreferenceManager.SongProvider.TagLib -> {
                val directories = context.contentResolver?.persistedUriPermissions
                    ?.filter { uriPermission -> uriPermission.isWritePermission }
                    ?.mapNotNull { uriPermission ->
                        SafDirectoryHelper.buildFolderNodeTree(context.contentResolver, uriPermission.uri).distinctUntilChanged()
                    }
                    .orEmpty()
                    .merge()
                    .toList()
                mediaImporter.import(TaglibSongProvider(context, fileScanner, directories))
            }
        }
    }

    // QueueChangeCallback Implementation

    override fun onQueueChanged() {
        view?.toggleSheet(visible = queueManager.getSize() != 0)
    }
}