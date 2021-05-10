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
import com.simplecityapps.mediaprovider.MediaProvider
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.dagger.Injectable
import com.simplecityapps.shuttle.ui.common.autoCleared
import com.simplecityapps.shuttle.ui.common.utils.withArgs
import com.simplecityapps.shuttle.ui.screens.onboarding.OnboardingChild
import com.simplecityapps.shuttle.ui.screens.onboarding.OnboardingPage
import com.simplecityapps.shuttle.ui.screens.onboarding.OnboardingParent
import com.squareup.phrase.Phrase
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

    @Inject
    lateinit var presenterFactory: ScannerPresenter.Factory
    lateinit var presenter: ScannerPresenter

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

        presenter = presenterFactory.create(shouldDismissOnScanComplete)

        toolbar = view.findViewById(R.id.toolbar)
        progressBar = view.findViewById(R.id.progressBar)
        titleTextView = view.findViewById(R.id.title)
        subtitleTextView = view.findViewById(R.id.subtitle)
        songCountTextView = view.findViewById(R.id.songCount)
        rescanButton = view.findViewById(R.id.rescan)

        rescanButton.setOnClickListener {
            presenter.startScan()
        }

        setTitle(getString(R.string.onboarding_media_scanner_tile))
        subtitleTextView.text = null
        progressBar.isVisible = false
        songCountTextView.isVisible = false
        rescanButton.isVisible = true

        toolbar.isVisible = shouldShowToolbar

        presenter.bindView(this)
    }

    override fun onResume() {
        super.onResume()

        getParent()?.showNextButton(getString(R.string.dialog_button_close))

        if (shouldScanAutomatically) {
            presenter.startScanOrExit()
        }
    }

    override fun onDestroyView() {
        presenter.unbindView()
        super.onDestroyView()
    }


    // ScannerContract.View Implementation

    override fun setTitle(title: String) {
        titleTextView.text = title
    }

    override fun dismiss() {
        getParent()?.exit()
    }

    override fun setScanStarted(providerType: MediaProvider.Type) {
        setTitle(getString(R.string.onboarding_media_scanner_scanning))
        subtitleTextView.text = Phrase.from(requireContext(), R.string.media_provider_title)
            .put("provider_type", providerType.title())
            .format()
        progressBar.isVisible = true
        progressBar.isIndeterminate = true
        rescanButton.isVisible = false
    }

    override fun setProgress(progress: Int, total: Int, message: String) {
        progressBar.progress = ((progress / total.toFloat()) * 100).toInt()
        subtitleTextView.text = message
        progressBar.isIndeterminate = false
        progressBar.isVisible = true
        songCountTextView.text = Phrase.from(requireContext(), R.string.media_provider_scan_progress)
            .put("progress", progress)
            .put("total", total)
            .format()
        songCountTextView.isVisible = true
        rescanButton.isVisible = false
    }

    override fun setScanComplete(providerType: MediaProvider.Type, inserts: Int, updates: Int, deletes: Int) {
        setTitle(
            Phrase.from(requireContext(), R.string.media_provider_scan_success_title)
                .put("provider_type", providerType.name)
                .format()
                .toString()
        )
        subtitleTextView.text = Phrase.from(requireContext(), R.string.media_provider_scan_success_subtitle)
            .put("inserts", inserts)
            .put("updates", updates)
            .put("deletes", deletes)
            .format()
        progressBar.isVisible = false
        songCountTextView.isVisible = false
    }

    override fun setAllScansComplete() {
        if (canShowRescanButton) {
            rescanButton.isVisible = true
        }
    }

    override fun setScanFailed() {
        setTitle(requireContext().getString(R.string.media_provider_scan_failure_title))
        subtitleTextView.text = requireContext().getString(R.string.media_provider_scan_failure_subtitle)
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
        private const val ARG_SHOW_TOOLBAR = "show_toolbar"

        fun newInstance(scanAutomatically: Boolean, showRescanButton: Boolean, dismissOnScanComplete: Boolean, showToolbar: Boolean) = MediaScannerFragment().withArgs {
            putBoolean(ARG_SCAN_AUTOMATICALLY, scanAutomatically)
            putBoolean(ARG_SHOW_RESCAN_BUTTON, showRescanButton)
            putBoolean(ARG_DISMISS_ON_SCAN_COMPLETE, dismissOnScanComplete)
            putBoolean(ARG_SHOW_TOOLBAR, showToolbar)
        }
    }
}