package com.simplecityapps.playback

import android.content.Intent
import com.simplecityapps.playback.widgets.WidgetManager

interface ActivityIntentProvider {

    fun provideMainActivityIntent(): Intent

    fun provideAppWidgetIntents(): List<Intent>

    fun updateAppWidgets(updateReason: WidgetManager.UpdateReason)

}