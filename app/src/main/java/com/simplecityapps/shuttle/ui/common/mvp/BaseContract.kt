package com.simplecityapps.shuttle.ui.common.mvp

class BaseContract {

    interface Presenter<T> {
        fun bindView(view: T)
        fun unbindView()
    }
}