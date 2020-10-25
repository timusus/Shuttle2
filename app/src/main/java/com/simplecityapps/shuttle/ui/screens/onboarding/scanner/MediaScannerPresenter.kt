package com.simplecityapps.shuttle.ui.screens.onboarding.scanner

import com.simplecityapps.localmediaprovider.local.provider.mediastore.MediaStoreSongProvider
import com.simplecityapps.localmediaprovider.local.provider.taglib.TaglibMediaProvider
import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.shuttle.ui.common.mvp.BaseContract
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named

interface ScannerContract {

    interface View {
        fun setProgress(progress: Int, total: Int, message: String)
        fun dismiss()
        fun setTitle(title: String)
        fun setScanComplete(inserts: Int, updates: Int, deletes: Int)
        fun setScanFailed()
    }

    interface Presenter : BaseContract.Presenter<View> {
        fun startScanOrExit()
        fun startScan()
        fun stopScan()
    }
}

class ScannerPresenter @Inject constructor(
    @Named("AppCoroutineScope") private val appCoroutineScope: CoroutineScope,
    private val mediaImporter: MediaImporter
) : ScannerContract.Presenter,
    BasePresenter<ScannerContract.View>() {

    private var scanJob: Job? = null

    override fun bindView(view: ScannerContract.View) {
        super.bindView(view)

        mediaImporter.listeners.add(listener)
    }

    override fun unbindView() {
        mediaImporter.listeners.remove(listener)
        super.unbindView()
    }


    // Private

    override fun startScanOrExit() {
        if (mediaImporter.isImporting) {
            return
        }
        when (mediaImporter.mediaProvider) {
            is TaglibMediaProvider -> {
                view?.setTitle("Reading song tags…")
            }
            is MediaStoreSongProvider -> {
                view?.setTitle("Scanning Media Store…")
            }
        }
        if (mediaImporter.importCount == 0) {
            startScan()
        } else {
            view?.dismiss()
        }
    }

    override fun startScan() {
        stopScan()
        scanJob = appCoroutineScope.launch {
            mediaImporter.import()
        }
    }

    override fun stopScan() {
        scanJob?.cancel()
    }


    // MediaImporter.Listener Implementation

    private val listener = object : MediaImporter.Listener {
        override fun onProgress(progress: Int, total: Int, song: Song) {
            when (mediaImporter.mediaProvider) {
                is TaglibMediaProvider -> {
                    view?.setTitle("Reading song tags…")
                }
                is MediaStoreSongProvider -> {
                    view?.setTitle("Scanning Media Store…")
                }
            }
            view?.setProgress(progress, total, "${song.albumArtist} - ${song.name}")
        }

        override fun onComplete(inserts: Int, updates: Int, deletes: Int) {
            view?.setScanComplete(inserts, updates, deletes)
        }

        override fun onFail() {
            view?.setScanFailed()
        }
    }
}