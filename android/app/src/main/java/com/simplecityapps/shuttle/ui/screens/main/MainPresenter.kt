package com.simplecityapps.shuttle.ui.screens.main

import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.shuttle.BuildConfig
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import com.simplecityapps.trial.Entitlement
import com.simplecityapps.trial.EntitlementRepository
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

interface MainContract {
    interface View {
        fun toggleSheet(visible: Boolean)

        fun showChangelog()

        fun showThankYouDialog()

        fun launchReviewFlow()
    }

    interface Presenter
}

class MainPresenter
@Inject
constructor(
    private val queueManager: QueueOperations,
    private val preferenceManager: GeneralPreferenceManager,
    private val entitlementRepository: EntitlementRepository
) : BasePresenter<MainContract.View>(),
    MainContract.Presenter {
    override fun bindView(view: MainContract.View) {
        super.bindView(view)

        val queueState = queueManager.queueStateFlow.value
        view.toggleSheet(visible = queueState.items.isNotEmpty())
        collectChanges(queueManager.queueStateFlow, queueState) { previous, current ->
            if (current.contentVersion != previous.contentVersion) {
                this.view?.toggleSheet(visible = current.items.isNotEmpty())
            }
        }

        if (preferenceManager.lastViewedChangelogVersion != BuildConfig.VERSION_NAME && preferenceManager.showChangelogOnLaunch) {
            view.showChangelog()
        }

        entitlementRepository.entitlement.onEach { entitlement ->
            if (entitlement is Entitlement.Pro) {
                if (preferenceManager.appPurchasedDate == null) {
                    preferenceManager.appPurchasedDate = Date()
                }
                if (!preferenceManager.hasSeenThankYouDialog) {
                    this.view?.showThankYouDialog()
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
}
