package com.simplecityapps.shuttle.ui.common.mvp

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
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
     * Collects [flow] on the main thread until [unbindView], passing [onChange] each new value with the
     * one before it.
     *
     * The value current at the call isn't passed on: a StateFlow replays it to every new collector, and
     * [bindView] renders the initial state itself. Collection starts undispatched, so the first value
     * compared against is exactly the one current when this is called.
     *
     * A StateFlow keeps only its latest value, so values set in quick succession can arrive as one change.
     */
    protected fun <V> collectChanges(
        flow: StateFlow<V>,
        onChange: (previous: V, current: V) -> Unit
    ) {
        launch(start = CoroutineStart.UNDISPATCHED) {
            var previous = flow.value
            var isInitialValue = true
            flow.collect { current ->
                if (isInitialValue) {
                    isInitialValue = false
                } else {
                    onChange(previous, current)
                }
                previous = current
            }
        }
    }
}
