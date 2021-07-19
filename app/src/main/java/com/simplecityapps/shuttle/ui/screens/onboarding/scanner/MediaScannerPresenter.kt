package com.simplecityapps.shuttle.ui.screens.onboarding.scanner

import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.mediaprovider.MediaProvider
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.shuttle.ui.common.mvp.BaseContract
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import com.squareup.inject.assisted.Assisted
import com.squareup.inject.assisted.AssistedInject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Named

interface ScannerContract {

    interface View {
        fun setImportStarted(providerType: MediaProvider.Type)
        fun setSongImportProgress(providerType: MediaProvider.Type, progress: Progress?, message: String)
        fun setSongImportComplete(providerType: MediaProvider.Type)
        fun setSongImportFailed(providerType: MediaProvider.Type, message: String?)
        fun setPlaylistImportProgress(providerType: MediaProvider.Type, progress: Progress?, message: String)
        fun setPlaylistImportComplete(providerType: MediaProvider.Type)
        fun setPlaylistImportFailed(providerType: MediaProvider.Type, message: String?)
        fun setAllScansComplete()
        fun dismiss()
    }

    interface Presenter : BaseContract.Presenter<View> {
        fun startScanOrExit()
        fun startScan()
        fun stopScan()
    }
}

class ScannerPresenter @AssistedInject constructor(
    @Named("AppCoroutineScope") private val appCoroutineScope: CoroutineScope,
    private val mediaImporter: MediaImporter,
    @Assisted private val shouldDismissOnScanComplete: Boolean
) : ScannerContract.Presenter,
    BasePresenter<ScannerContract.View>() {

    @AssistedInject.Factory
    interface Factory {
        fun create(shouldDismissOnScanComplete: Boolean): ScannerPresenter
    }

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

    /**
     * It's possible that during onboarding, a scan has completed while the screen was off, so this view was never automatically dismissed.
     * So, if the 'shouldDismissOnScanComplete' flag is set to true, and we've already scanned at least once before, we simply exit.
     * Otherwise, we attempt to scan.
     *
     * Note, if a scan is in progress, this function is a no-op.
     */
    override fun startScanOrExit() {
        if (mediaImporter.isImporting) {
            return
        }

        if (shouldDismissOnScanComplete && mediaImporter.importCount != 0) {
            view?.dismiss()
        } else {
            startScan()
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

        override fun onStart(providerType: MediaProvider.Type) {
            view?.setImportStarted(providerType)
        }

        override fun onSongImportProgress(providerType: MediaProvider.Type, message: String, progress: Progress?) {
            view?.setSongImportProgress(
                providerType = providerType,
                progress = progress,
                message = message
            )
        }

        override fun onPlaylistImportProgress(providerType: MediaProvider.Type, message: String, progress: Progress?) {
            view?.setPlaylistImportProgress(
                providerType = providerType,
                progress = progress,
                message = message
            )
        }

        override fun onSongImportComplete(providerType: MediaProvider.Type) {
            view?.setSongImportComplete(providerType)
        }

        override fun onPlaylistImportComplete(providerType: MediaProvider.Type) {
            view?.setPlaylistImportComplete(providerType)
        }

        override fun onSongImportFailed(providerType: MediaProvider.Type, message: String?) {
            view?.setSongImportFailed(providerType, message)
        }

        override fun onPlaylistImportFailed(providerType: MediaProvider.Type, message: String?) {
            view?.setPlaylistImportFailed(providerType, message)
        }

        override fun onAllComplete() {
            view?.setAllScansComplete()
            if (shouldDismissOnScanComplete) {
                view?.dismiss()
            }
        }
    }
}