package com.simplecityapps.shuttle.ui.screens.main

import com.simplecityapps.playback.queue.QueueChangeCallback
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.playback.queue.QueueWatcher
import com.simplecityapps.shuttle.BuildConfig
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import javax.inject.Inject

interface MainContract {

    interface View {
        fun toggleSheet(visible: Boolean)
        fun showChangelog()
    }

    interface Presenter
}

class MainPresenter @Inject constructor(
    private val queueManager: QueueManager,
    private val queueWatcher: QueueWatcher,
    private val preferenceManager: GeneralPreferenceManager
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