package com.simplecityapps.shuttle.ui.screens.settings

import androidx.annotation.NavigationRes
import com.simplecityapps.shuttle.ui.common.mvp.BaseContract
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import javax.inject.Inject

interface BottomDrawerSettingsContract {

    interface View {
        fun setData(settingsItems: List<SettingsMenuItem>, currentDestination: Int?)
    }

    interface Presenter : BaseContract.Presenter<View> {
        fun loadData()
    }
}

class BottomDrawerSettingsPresenter @Inject constructor() :
    BasePresenter<BottomDrawerSettingsContract.View>(),
    BottomDrawerSettingsContract.Presenter {

    @NavigationRes
    var currentDestinationIdRes: Int? = null
        set(value) {
            field = value
            view?.setData(
                SettingsMenuItem.values().toList(),
                value
            )
        }

    override fun loadData() {
        view?.setData(
            SettingsMenuItem.values().toList(),
            currentDestinationIdRes
        )
    }
}