package com.simplecityapps.shuttle.appinitializers

import android.annotation.SuppressLint
import android.app.Application
import com.simplecityapps.shuttle.ui.widgets.WidgetManager
import javax.inject.Inject

class WidgetInitializer
@Inject
constructor(
    private val widgetManager: WidgetManager
) : AppInitializer {
    @SuppressLint("BinaryOperationInTimber")
    override fun init(application: Application) {
        widgetManager.registerCallbacks()
    }
}
