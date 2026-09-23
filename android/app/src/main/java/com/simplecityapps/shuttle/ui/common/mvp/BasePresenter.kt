package com.simplecityapps.shuttle.ui.common.mvp

import com.simplecityapps.shuttle.coroutines.launchCollectingChanges
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber

abstract class BasePresenter<T : Any> :
    BaseContract.Presenter<T>,
    CoroutineScope {
    var view: T? = null

    private var job = SupervisorJob()

    val exceptionHandler by lazy {
        CoroutineExceptionHandler { _, exception -> Timber.e(exception) }
    }

    override val coroutineContext: CoroutineContext
        get() = job + exceptionHandler + Dispatchers.Main

    override fun bindView(view: T) {
        if (job.isCancelled) {
            job = SupervisorJob()
        }
        this.view = view
    }

    override fun unbindView() {
        job.cancel()
        view = null
    }

    /**
     * Collects [flow] on the main thread until [unbindView], passing [onChange] each value that differs
     * from the last one rendered, with that last one.
     *
     * [rendered] is the snapshot of [flow] that [bindView] drew the screen from (or, where the screen is
     * drawn from a live read instead, a snapshot taken before that read). The first comparison is against
     * it rather than whatever [flow] holds when collection starts, so a change made between the draw and
     * the start of collection is still delivered, however the two are ordered.
     *
     * A StateFlow keeps only its latest value, so values set in quick succession can arrive as one change.
     */
    protected fun <V> collectChanges(
        flow: StateFlow<V>,
        rendered: V,
        onChange: (previous: V, current: V) -> Unit
    ) {
        launchCollectingChanges(flow, rendered, onChange = onChange)
    }
}
