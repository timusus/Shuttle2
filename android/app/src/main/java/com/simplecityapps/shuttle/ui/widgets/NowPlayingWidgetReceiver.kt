package com.simplecityapps.shuttle.ui.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import dagger.hilt.android.EntryPointAccessors

/**
 * Receivers for the small and large now playing widgets. Both draw the same [NowPlayingWidget]; they're
 * separate so the picker offers two default sizes. The class names are what placed widgets are bound to,
 * so they must not change.
 */
abstract class NowPlayingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NowPlayingWidget()

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        // A widget was just placed or the app updated: make sure the shared state reflects what's playing.
        EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java).widgetManager().requestUpdate()
    }
}

class WidgetProvider41 : NowPlayingWidgetReceiver()

class WidgetProvider42 : NowPlayingWidgetReceiver()
