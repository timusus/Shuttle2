package com.simplecityapps.shuttle.ui.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.util.LruCache
import android.widget.RemoteViews
import androidx.annotation.LayoutRes
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.playback.PlaybackService
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.pendingintent.PendingIntentCompat
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
abstract class ShuttleAppWidgetProvider : AppWidgetProvider() {

    @Inject
    lateinit var playbackManager: PlaybackManager

    @Inject
    lateinit var queueManager: QueueManager

    @Inject
    lateinit var artworkCache: LruCache<String, Bitmap?>

    @Inject
    lateinit var preferenceManager: GeneralPreferenceManager

    @Inject
    lateinit var imageLoader: ArtworkImageLoader

    internal var updateReason = WidgetManager.UpdateReason.Unknown

    @get:LayoutRes
    abstract val layoutResIdLight: Int

    @get:LayoutRes
    abstract val layoutResIdDark: Int

    val isDarkMode: Boolean
        get() = preferenceManager.widgetDarkMode

    private fun getLayoutResId(): Int {
        return if (isDarkMode) layoutResIdDark else layoutResIdLight
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        updateReason = WidgetManager.UpdateReason.values()[intent?.extras?.getInt(WidgetManager.ARG_UPDATE_REASON) ?: WidgetManager.UpdateReason.Unknown.ordinal]

        super.onReceive(context, intent)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)

        appWidgetIds.forEach { appWidgetId ->
            val contentIntent: PendingIntent = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntentCompat.FLAG_IMMUTABLE)

            val views = RemoteViews(context.packageName, getLayoutResId()).apply {
                bind(context, appWidgetId, contentIntent, imageLoader, appWidgetManager, preferenceManager.widgetBackgroundTransparency / 100f)
            }

            // Tell the AppWidgetManager to perform an update on the current app widget
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    abstract fun RemoteViews.bind(
        context: Context,
        appWidgetId: Int,
        contentIntent: PendingIntent,
        imageLoader: ArtworkImageLoader,
        appWidgetManager: AppWidgetManager,
        backgroundTransparency: Float
    )

    internal fun playbackPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, PlaybackService::class.java).apply {
            action = PlaybackService.ACTION_TOGGLE_PLAYBACK
        }
        return getPendingIntent(context, intent)
    }

    internal fun prevPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, PlaybackService::class.java).apply {
            action = PlaybackService.ACTION_SKIP_PREV
        }
        return getPendingIntent(context, intent)
    }

    internal fun nextPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, PlaybackService::class.java).apply {
            action = PlaybackService.ACTION_SKIP_NEXT
        }
        return getPendingIntent(context, intent)
    }

    private fun getPendingIntent(context: Context, intent: Intent): PendingIntent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(context, 1, intent, PendingIntentCompat.FLAG_IMMUTABLE)
        } else {
            PendingIntent.getService(context, 1, intent, PendingIntentCompat.FLAG_IMMUTABLE)
        }
    }

    fun getPlaybackDrawable(): Int {
        return when (playbackManager.playbackState()) {
            is PlaybackState.Loading, PlaybackState.Playing -> {
                if (isDarkMode) R.drawable.ic_pause_white_24dp else R.drawable.ic_pause_black_24dp
            }
            else -> {
                if (isDarkMode) R.drawable.ic_play_arrow_white_24dp else R.drawable.ic_play_arrow_black_24dp
            }
        }
    }

    fun getPlaceholderDrawable(): Int {
        return if (isDarkMode) R.drawable.ic_music_note_white_24dp else R.drawable.ic_music_note_black_24dp
    }
}
