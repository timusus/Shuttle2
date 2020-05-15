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
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import javax.inject.Inject

interface MainContract {

    interface View {
        fun toggleSheet(visible: Boolean)
        fun showChangelog()
    }

    interface Presenter {
        fun scanMedia()
    }
}

class MainPresenter @Inject constructor(
    private val context: Context,
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
        if (mediaImporter.scanCount < 1) {
            scanMedia()
        }
    }

    override fun unbindView() {
        super.unbindView()

        queueWatcher.removeCallback(this)
    }

    override fun scanMedia() {
        when (playbackPreferenceManager.songProvider) {
            PlaybackPreferenceManager.SongProvider.MediaStore -> {
                mediaImporter.startScan(MediaStoreSongProvider(context.applicationContext))
            }
            PlaybackPreferenceManager.SongProvider.TagLib -> {
                addDisposable(
                    Single.fromCallable {
                            context.contentResolver?.persistedUriPermissions
                                ?.filter { uriPermission -> uriPermission.isReadPermission }
                                ?.mapNotNull { uriPermission ->
                                    SafDirectoryHelper.buildFolderNodeTree(context.contentResolver, uriPermission.uri)
                                }.orEmpty()
                        }
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribeBy(
                            onSuccess = { nodes ->
                                mediaImporter.startScan(TaglibSongProvider(context, fileScanner, nodes))
                            },
                            onError = { throwable -> Timber.e(throwable, "Failed to scan library") }
                        )
                )
            }
        }
    }

    // QueueChangeCallback Implementation

    override fun onQueueChanged() {
        view?.toggleSheet(visible = queueManager.getSize() != 0)
    }
}