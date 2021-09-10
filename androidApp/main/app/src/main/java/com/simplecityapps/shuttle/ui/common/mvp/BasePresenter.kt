package com.simplecityapps.shuttle.ui.common.mvp

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import timber.log.Timber
import kotlin.coroutines.CoroutineContext

abstract class BasePresenter<T : Any> : BaseContract.Presenter<T>, CoroutineScope {

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
}