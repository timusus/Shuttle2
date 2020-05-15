package com.simplecityapps.shuttle.ui.screens.onboarding.scanner

import android.content.Context
import com.simplecityappds.saf.SafDirectoryHelper
import com.simplecityapps.localmediaprovider.local.provider.mediastore.MediaStoreSongProvider
import com.simplecityapps.localmediaprovider.local.provider.taglib.FileScanner
import com.simplecityapps.localmediaprovider.local.provider.taglib.TaglibSongProvider
import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.shuttle.ui.common.mvp.BaseContract
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import javax.inject.Inject

interface ScannerContract {

    sealed class ScanType {
        object MediaStore : ScanType()
        class Taglib(val directories: List<SafDirectoryHelper.DocumentNodeTree>) : ScanType()
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
                is ScannerContract.ScanType.Taglib -> TaglibSongProvider(context, fileScanner, scanType.directories)
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