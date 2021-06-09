package com.simplecityapps.shuttle.ui.screens.main

import com.simplecityapps.playback.queue.QueueChangeCallback
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.playback.queue.QueueWatcher
import com.simplecityapps.shuttle.BuildConfig
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import com.simplecityapps.trial.TrialManager
import com.simplecityapps.trial.TrialState
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.util.*
import javax.inject.Inject

interface MainContract {

    interface View {
        fun toggleSheet(visible: Boolean)
        fun showChangelog()
        fun showTrialDialog()
        fun showThankYouDialog()
    }

    interface Presenter
}

class MainPresenter @Inject constructor(
    private val queueManager: QueueManager,
    private val queueWatcher: QueueWatcher,
    private val preferenceManager: GeneralPreferenceManager,
    private val trialManager: TrialManager
) : MainContract.Presenter,
    BasePresenter<MainContract.View>(),
    QueueChangeCallback {

    override fun bindView(view: MainContract.View) {
        super.bindView(view)

        queueWatcher.addCallback(this)

        view.toggleSheet(visible = queueManager.getSize() != 0)

        if (preferenceManager.lastViewedChangelogVersion != BuildConfig.VERSION_NAME && preferenceManager.showChangelogOnLaunch) {
            view.showChangelog()
        }

        trialManager.trialState.onEach { trialState ->
            when (trialState) {
                is TrialState.Trial -> {
                    // Show the trial dialog once every 3 days
                    if (preferenceManager.lastViewedTrialDialog.before(Date(Date().time - 4 * 24 * 60 * 60 * 1000))) {
                        this.view?.showTrialDialog()
                    }
                }
                is TrialState.Expired -> {
                    // Show the trial dialog once a day
                    if (preferenceManager.lastViewedTrialDialog.before(Date(Date().time - 1 * 24 * 60 * 60 * 1000))) {
                        this.view?.showTrialDialog()
                    }
                }
                is TrialState.Paid -> {
                    if (!preferenceManager.hasSeenThankYouDialog) {
                        this.view?.showThankYouDialog()
                    }
                }
            }
        }.launchIn(this)
    }

    override fun unbindView() {
        super.unbindView()

        queueWatcher.removeCallback(this)
    }


    // QueueChangeCallback Implementation

    override fun onQueueChanged() {
        view?.toggleSheet(visible = queueManager.getSize() != 0)
    }
}