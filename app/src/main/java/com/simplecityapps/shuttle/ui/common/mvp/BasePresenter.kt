package com.simplecityapps.shuttle.ui.common.mvp

import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable

abstract class BasePresenter<T : Any> : BaseContract.Presenter<T> {

    var view: T? = null

    private val compositeDisposable = CompositeDisposable()

    override fun bindView(view: T) {
        this.view = view
    }

    override fun unbindView() {
        compositeDisposable.clear()
        view = null
    }

    fun addDisposable(disposable: Disposable) {
        compositeDisposable.add(disposable)
    }
}