package com.simplecityapps.shuttle.ui.screens.onboarding.scanner

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.dagger.Injectable
import com.simplecityapps.shuttle.ui.common.autoCleared
import com.simplecityapps.shuttle.ui.common.utils.withArgs
import com.simplecityapps.shuttle.ui.screens.onboarding.OnboardingChild
import com.simplecityapps.shuttle.ui.screens.onboarding.OnboardingPage
import com.simplecityapps.shuttle.ui.screens.onboarding.OnboardingParent
import timber.log.Timber
import javax.inject.Inject

class MediaScannerFragment :
    Fragment(),
    Injectable,
    ScannerContract.View,
    OnboardingChild {

    private var toolbar: Toolbar by autoCleared()
    private var progressBar: ProgressBar by autoCleared()
    private var titleTextView: TextView by autoCleared()
    private lateinit var subtitleTextView: TextView
    private var songCountTextView: TextView by autoCleared()
    private var rescanButton: Button by autoCleared()

    @Inject lateinit var presenter: ScannerPresenter

    private var shouldScanAutomatically: Boolean = false
    private var canShowRescanButton: Boolean = false
    private var shouldDismissOnScanComplete: Boolean = false
    private var shouldShowToolbar: Boolean = false


    // Lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        shouldScanAutomatically = requireArguments().getBoolean(ARG_SCAN_AUTOMATICALLY)
        canShowRescanButton = requireArguments().getBoolean(ARG_SHOW_RESCAN_BUTTON)
        shouldDismissOnScanComplete = requireArguments().getBoolean(ARG_DISMISS_ON_SCAN_COMPLETE)
        shouldShowToolbar = requireArguments().getBoolean(ARG_SHOW_TOOLBAR)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_scanner, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        toolbar = view.findViewById(R.id.toolbar)
        progressBar = view.findViewById(R.id.progressBar)
        titleTextView = view.findViewById(R.id.title)
        subtitleTextView = view.findViewById(R.id.subtitle)
        songCountTextView = view.findViewById(R.id.songCount)
        rescanButton = view.findViewById(R.id.rescan)

        rescanButton.setOnClickListener {
            presenter.startScan()
        }

        setTitle("No scan in progress")
        subtitleTextView.text = "What is my purpose?"
        progressBar.isVisible = false
        songCountTextView.isVisible = false
        rescanButton.isVisible = true

        toolbar.isVisible = shouldShowToolbar

        presenter.bindView(this)
    }

    override fun onResume() {
        super.onResume()

        getParent()?.showNextButton("Close")

        if (shouldScanAutomatically) {
            presenter.startScanOrExit()
        }
    }

    override fun onDestroyView() {
        presenter.unbindView()
        super.onDestroyView()
    }


    // ScannerContract.View Implementation

    override fun setProgress(progress: Int, total: Int, message: String) {
        progressBar.progress = ((progress / total.toFloat()) * 100).toInt()
        subtitleTextView.text = message
        progressBar.isIndeterminate = false
        progressBar.isVisible = true
        songCountTextView.text = "$progress/$total songs scanned"
        songCountTextView.isVisible = true
        rescanButton.isVisible = false
    }

    override fun setTitle(title: String) {
        titleTextView.text = title
    }

    override fun dismiss() {
        Timber.i("Dismiss called")
        if (shouldDismissOnScanComplete) {
            getParent()?.exit()
        }
    }

    override fun setScanComplete(inserts: Int, updates: Int, deletes: Int) {
        setTitle("Scan complete")
        subtitleTextView.text = "$inserts inserts, $updates updates, $deletes deletes"
        progressBar.isVisible = false
        songCountTextView.isVisible = false

        if (canShowRescanButton) {
            rescanButton.isVisible = true
        }

        dismiss()
    }

    override fun setScanFailed() {
        setTitle("Scan failed")
        subtitleTextView.text = "No songs imported"
        progressBar.isVisible = false
        songCountTextView.isVisible = false
        rescanButton.isVisible = true
    }


    // OnboardingChild Implementation

    override val page: OnboardingPage = OnboardingPage.Scanner

    override fun getParent(): OnboardingParent? {
        return parentFragment as? OnboardingParent
    }

    override fun handleNextButtonClick() {
        getParent()?.exit()
    }

    override fun handleBackButtonClick() {
        presenter.stopScan()
        getParent()?.goToPrevious()
    }


    // Static

    companion object {

        private const val ARG_SCAN_AUTOMATICALLY = "scan_automatically"
        private const val ARG_SHOW_RESCAN_BUTTON = "show_rescan_button"
        private const val ARG_DISMISS_ON_SCAN_COMPLETE = "dismiss_on_complete"
        private const val ARG_SHOW_TOOLBAR = "dismiss_on_complete"

        fun newInstance(scanAutomatically: Boolean, showRescanButton: Boolean, dismissOnScanComplete: Boolean, showToolbar: Boolean) = MediaScannerFragment().withArgs {
            putBoolean(ARG_SCAN_AUTOMATICALLY, scanAutomatically)
            putBoolean(ARG_SHOW_RESCAN_BUTTON, showRescanButton)
            putBoolean(ARG_DISMISS_ON_SCAN_COMPLETE, dismissOnScanComplete)
            putBoolean(ARG_SHOW_TOOLBAR, showToolbar)
        }
    }
}