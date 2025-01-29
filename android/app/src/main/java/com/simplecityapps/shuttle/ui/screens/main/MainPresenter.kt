package com.simplecityapps.shuttle.ui.screens.main

import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.simplecityapps.playback.queue.QueueChangeCallback
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.playback.queue.QueueWatcher
import com.simplecityapps.shuttle.BuildConfig
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import com.simplecityapps.trial.TrialManager
import com.simplecityapps.trial.TrialState
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

interface MainContract {
    interface View {
        fun toggleSheet(visible: Boolean)

        fun showChangelog()

        fun showTrialDialog()

        fun showThankYouDialog()

        fun launchReviewFlow()

        fun showCrashReportingDialog()
    }

    interface Presenter {
        fun onCrashReportingToggled(enabled: Boolean)
    }
}

class MainPresenter
@Inject
constructor(
    private val queueManager: QueueManager,
    private val queueWatcher: QueueWatcher,
    private val preferenceManager: GeneralPreferenceManager,
    private val trialManager: TrialManager
) : BasePresenter<MainContract.View>(),
    MainContract.Presenter,
    QueueChangeCallback {
    override fun bindView(view: MainContract.View) {
        super.bindView(view)

        queueWatcher.addCallback(this)

        view.toggleSheet(visible = queueManager.getSize() != 0)

        if (preferenceManager.lastViewedChangelogVersion != BuildConfig.VERSION_NAME && preferenceManager.showChangelogOnLaunch) {
            view.showChangelog()
        }

        if (!preferenceManager.crashReportingEnabled && !preferenceManager.hasSeenCrashReportingDialog) {
            if (BuildConfig.VERSION_NAME.contains("alpha") || BuildConfig.VERSION_NAME.contains("beta")) {
                this.view?.showCrashReportingDialog()
                preferenceManager.hasSeenCrashReportingDialog = true
            }
        }

        trialManager.trialState.onEach { trialState ->
            when (trialState) {
                is TrialState.Pretrial, is TrialState.Unknown -> {
                    // Nothing to do
                }

                is TrialState.Trial -> {
                    // Show the trial dialog once every 3 days
                    if (preferenceManager.lastViewedTrialDialogDate == null || preferenceManager.lastViewedTrialDialogDate?.before(Date(Date().time - 4 * 24 * 60 * 60 * 1000)) == true) {
                        this.view?.showTrialDialog()
                    }
                }

                is TrialState.Expired -> {
                    // Show the trial dialog once a day
                    if (preferenceManager.lastViewedTrialDialogDate == null || preferenceManager.lastViewedTrialDialogDate?.before(Date(Date().time - 1 * 24 * 60 * 60 * 1000)) == true) {
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

        // If it's been a week since the app was purchased
        if (preferenceManager.appPurchasedDate?.before(Date(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7))) == true) {
            if (preferenceManager.lastViewedRatingFlow == null || preferenceManager.lastViewedRatingFlow?.before(Date(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30))) == true) {
                // If the rating dialog hasn't been shown before, or it's been 30 days since it was shown
                preferenceManager.lastViewedRatingFlow = Date()
                this.view?.launchReviewFlow()
            }
        }
    }

    override fun unbindView() {
        super.unbindView()

        queueWatcher.removeCallback(this)
    }

    // QueueChangeCallback Implementation

    override fun onQueueChanged(reason: QueueChangeCallback.QueueChangeReason) {
        view?.toggleSheet(visible = queueManager.getSize() != 0)
    }

    override fun onCrashReportingToggled(enabled: Boolean) {
        preferenceManager.crashReportingEnabled = enabled
        Firebase.crashlytics.setCrashlyticsCollectionEnabled(enabled)
    }
}
