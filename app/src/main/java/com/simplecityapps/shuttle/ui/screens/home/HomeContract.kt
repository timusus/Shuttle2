package com.simplecityapps.shuttle.ui.screens.home

interface HomeContract {

    interface View {

        fun showLoadError(error: Error)

    }

    interface Presenter {

        fun shuffleAll()
    }

}