package com.simplecityapps.shuttle.ui.screens.onboarding.scanner

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.dagger.Injectable
import com.simplecityapps.shuttle.ui.common.autoCleared
import com.simplecityapps.shuttle.ui.screens.onboarding.OnboardingChild
import com.simplecityapps.shuttle.ui.screens.onboarding.OnboardingPage
import com.simplecityapps.shuttle.ui.screens.onboarding.OnboardingParent
import javax.inject.Inject

class MediaScannerFragment :
    Fragment(),
    Injectable,
    ScannerContract.View,
    OnboardingChild {

    private var progressBar: ProgressBar by autoCleared()
    private var titleTextView: TextView by autoCleared()
    private var subtitleTextView: TextView by autoCleared()
    private var songCountTextView: TextView by autoCleared()

    @Inject lateinit var presenter: ScannerPresenter

    private var scanProgress = 0


    // Lifecycle

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_scanner, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        progressBar = view.findViewById(R.id.progressBar)
        titleTextView = view.findViewById(R.id.title)
        subtitleTextView = view.findViewById(R.id.subtitle)
        songCountTextView = view.findViewById(R.id.songCount)

        presenter.bindView(this)

        if (getParent().directories == null) {
            titleTextView.text = "Scanning Media Store…"
        } else {
            titleTextView.text = "Reading song tags…"
        }
    }

    override fun onResume() {
        super.onResume()

        getParent().showNextButton("Close")

        val scanType = when (val selectedUris = getParent().directories) {
            null -> ScannerContract.ScanType.MediaStore
            else -> ScannerContract.ScanType.Taglib(selectedUris)
        }

        scanProgress = 0
        presenter.startScan(scanType)
    }

    override fun onDestroyView() {
        presenter.unbindView()
        super.onDestroyView()
    }


    // ScannerContract.View Implementation

    override fun setProgress(progress: Float, message: String) {
        scanProgress++
        progressBar.progress = (progress * 100).toInt()
        subtitleTextView.text = message
        progressBar.isIndeterminate = false
        songCountTextView.text = "$scanProgress songs scanned"
    }

    override fun dismiss() {
        getParent().exit()
    }

    // OnboardingChild Implementation

    override val page: OnboardingPage = OnboardingPage.Scanner

    override fun getParent(): OnboardingParent {
        return parentFragment as OnboardingParent
    }

    override fun handleNextButtonClick() {
        getParent().exit()
    }

    override fun handleBackButtonClick() {
        presenter.stopScan()
        getParent().goToPrevious()
    }
}