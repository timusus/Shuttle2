package com.simplecityapps.shuttle.ui.screens.onboarding.scanner

import android.content.Context
import android.net.Uri
import com.simplecityapps.localmediaprovider.local.provider.mediastore.MediaStoreSongProvider
import com.simplecityapps.localmediaprovider.local.provider.taglib.TaglibSongProvider
import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.shuttle.ui.common.mvp.BaseContract
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import com.simplecityapps.taglib.FileScanner
import javax.inject.Inject

interface ScannerContract {

    sealed class ScanType {
        object MediaStore : ScanType()
        class Taglib(val uriMimeTypePairs: List<Pair<Uri, String>>) : ScanType()
    }

    interface View {
        fun setProgress(progress: Float, message: String)
        fun dismiss()
    }

    interface Presenter : BaseContract.Presenter<View> {
        fun startScan(scanType: ScanType)
        fun stopScan()
    }
}

class ScannerPresenter @Inject constructor(
    private val context: Context,
    private val fileScanner: FileScanner,
    private val mediaImporter: MediaImporter
) : ScannerContract.Presenter,
    BasePresenter<ScannerContract.View>() {

    override fun bindView(view: ScannerContract.View) {
        super.bindView(view)
        mediaImporter.listeners.add(listener)
    }

    override fun unbindView() {
        mediaImporter.listeners.remove(listener)
        super.unbindView()
    }

    override fun startScan(scanType: ScannerContract.ScanType) {
        mediaImporter.startScan(
            when (scanType) {
                is ScannerContract.ScanType.MediaStore -> MediaStoreSongProvider(context)
                is ScannerContract.ScanType.Taglib -> TaglibSongProvider(context, fileScanner, scanType.uriMimeTypePairs)
            }
        )
    }

    override fun stopScan() {
        mediaImporter.stopScan()
    }

    private val listener = object : MediaImporter.Listener {
        override fun onProgress(progress: Float, message: String) {
            view?.setProgress(progress, message)
        }

        override fun onComplete() {
            view?.dismiss()
        }
    }
}