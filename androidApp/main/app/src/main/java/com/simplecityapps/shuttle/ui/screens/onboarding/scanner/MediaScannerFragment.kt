package com.simplecityapps.shuttle.ui.screens.onboarding.scanner

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.simplecityapps.adapter.RecyclerAdapter
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.ui.common.autoCleared
import com.simplecityapps.shuttle.ui.common.recyclerview.SpacesItemDecoration
import com.simplecityapps.shuttle.ui.common.utils.withArgs
import com.simplecityapps.shuttle.ui.screens.onboarding.OnboardingChild
import com.simplecityapps.shuttle.ui.screens.onboarding.OnboardingPage
import com.simplecityapps.shuttle.ui.screens.onboarding.OnboardingParent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

sealed class ImportProgressState {
    object Unknown : ImportProgressState()
    data class InProgress(val progress: Progress?, val message: String) : ImportProgressState()
    object Complete : ImportProgressState()
    data class Failed(val message: String?) : ImportProgressState()
}

@AndroidEntryPoint
class MediaScannerFragment :
    Fragment(),
    ScannerContract.View,
    OnboardingChild {

    private var toolbar: Toolbar by autoCleared()
    private var rescanButton: Button by autoCleared()
    private var recyclerView: RecyclerView by autoCleared()
    private var adapter: RecyclerAdapter by autoCleared()

    @Inject
    lateinit var presenterFactory: ScannerPresenter.Factory
    lateinit var presenter: ScannerPresenter

    private var shouldScanAutomatically: Boolean = false
    private var canShowRescanButton: Boolean = false
    private var shouldDismissOnScanComplete: Boolean = false
    private var shouldShowToolbar: Boolean = false

    private var songImportProgress = mutableMapOf<MediaProviderType, ImportProgressState>()
    private var playlistImportProgress = mutableMapOf<MediaProviderType, ImportProgressState>()

    private var scanCompleted = false

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
        rescanButton = view.findViewById(R.id.rescan)
        recyclerView = view.findViewById(R.id.recyclerView)
        recyclerView.addItemDecoration(SpacesItemDecoration(8))
        adapter = RecyclerAdapter(scope = viewLifecycleOwner.lifecycleScope, skipIntermediateUpdates = false)
        recyclerView.adapter = adapter

        rescanButton.isVisible = canShowRescanButton
        rescanButton.setOnClickListener {
            presenter.startScan()
        }

        toolbar.isVisible = shouldShowToolbar

        presenter.bindView(this)
    }

    override fun onResume() {
        super.onResume()

        getParent()?.showNextButton(getString(R.string.dialog_button_close))

        if (shouldScanAutomatically && !scanCompleted) {
            presenter.startScanOrExit()
        }
    }

    override fun onDestroyView() {
        presenter.unbindView()
        super.onDestroyView()
    }

    // Private

    private fun updateImportProgress() {
        adapter.update(
            (songImportProgress.keys + playlistImportProgress.keys).map { key ->
                ScanProgressBinder(
                    key,
                    songImportProgress[key] ?: ImportProgressState.Unknown,
                    playlistImportProgress[key] ?: ImportProgressState.Unknown
                )
            }
        )
    }

    // ScannerContract.View Implementation

    override fun dismiss() {
        getParent()?.exit()
    }

    override fun setImportStarted(providerType: MediaProviderType) {
        updateImportProgress()
        rescanButton.isVisible = false
    }

    override fun setSongImportProgress(providerType: MediaProviderType, progress: Progress?, message: String) {
        songImportProgress[providerType] = ImportProgressState.InProgress(progress, message)
        updateImportProgress()
    }

    override fun setSongImportComplete(providerType: MediaProviderType) {
        songImportProgress[providerType] = ImportProgressState.Complete
        updateImportProgress()
    }

    override fun setSongImportFailed(providerType: MediaProviderType, message: String?) {
        songImportProgress[providerType] = ImportProgressState.Failed(message)
        updateImportProgress()
    }

    override fun setPlaylistImportProgress(providerType: MediaProviderType, progress: Progress?, message: String) {
        playlistImportProgress[providerType] = ImportProgressState.InProgress(progress, message)
        updateImportProgress()
    }

    override fun setPlaylistImportComplete(providerType: MediaProviderType) {
        playlistImportProgress[providerType] = ImportProgressState.Complete
        updateImportProgress()
    }

    override fun setPlaylistImportFailed(providerType: MediaProviderType, message: String?) {
        playlistImportProgress[providerType] = ImportProgressState.Failed(message)
        updateImportProgress()
    }

    override fun setAllScansComplete() {
        if (canShowRescanButton) {
            rescanButton.isVisible = true
        }
        scanCompleted = true
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
