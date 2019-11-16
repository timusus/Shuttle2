package com.simplecityapps.shuttle.ui.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import au.com.simplecityapps.shuttle.imageloading.glide.GlideImageLoader
import com.bumptech.glide.request.target.AppWidgetTarget
import com.simplecityapps.playback.ActivityIntentProvider
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.playback.PlaybackService
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.playback.widgets.WidgetManager
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.utils.dp
import dagger.android.AndroidInjection
import timber.log.Timber
import javax.inject.Inject

class ShuttleAppWidgetProvider : AppWidgetProvider() {

    @Inject lateinit var playbackManager: PlaybackManager
    @Inject lateinit var queueManager: QueueManager

    private var target: AppWidgetTarget? = null

    private var updateReason = WidgetManager.UpdateReason.Unknown

    override fun onReceive(context: Context?, intent: Intent?) {

        AndroidInjection.inject(this, context)

        updateReason = WidgetManager.UpdateReason.values()[intent?.extras?.getInt(WidgetManager.ARG_UPDATE_REASON) ?: WidgetManager.UpdateReason.Unknown.ordinal]

        super.onReceive(context, intent)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)

        val imageLoader = GlideImageLoader(context)


        // Perform this loop procedure for each App Widget that belongs to this provider
        appWidgetIds.forEach { appWidgetId ->

            // Create an Intent to launch ExampleActivity
            val contentIntent: PendingIntent = (context.applicationContext as ActivityIntentProvider).provideMainActivityIntent()
                .let { intent -> PendingIntent.getActivity(context, 0, intent, 0) }


            // Get the layout for the App Widget and attach an on-click listener
            // to the button
            val views = RemoteViews(context.packageName, R.layout.appwidget)
                .apply {
                    setOnClickPendingIntent(R.id.container, contentIntent)
                    setOnClickPendingIntent(R.id.playPauseButton, playbackPendingIntent(context))
                    setOnClickPendingIntent(R.id.nextButton, nextPendingIntent(context))

                    queueManager.getCurrentItem()?.let { currentItem ->
                        setViewVisibility(R.id.subtitle, View.VISIBLE)
                        if (updateReason == WidgetManager.UpdateReason.QueueChanged
                            || updateReason == WidgetManager.UpdateReason.QueuePositionChanged
                            || updateReason == WidgetManager.UpdateReason.Unknown
                        ) {
                            setTextViewText(R.id.title, currentItem.song.name)
                            setTextViewText(R.id.subtitle, "${currentItem.song.albumArtistName} • ${currentItem.song.albumName}")
                            setImageViewResource(R.id.artwork, R.drawable.ic_music_note_black_24dp)
                            target = AppWidgetTarget(context, 44.dp, 44.dp, R.id.artwork, this, appWidgetId)
                            imageLoader.loadIntoRemoteViews(currentItem.song, target!!, ArtworkImageLoader.Options.RoundedCorners(4.dp))
                        }
                    } ?: run {
                        setTextViewText(R.id.title, "Choose a song…")
                        setViewVisibility(R.id.subtitle, View.GONE)
                    }

                    if (updateReason == WidgetManager.UpdateReason.PlaystateChanged || updateReason == WidgetManager.UpdateReason.Unknown) {
                        setImageViewResource(R.id.playPauseButton, if (playbackManager.isPlaying()) R.drawable.ic_pause_black_24dp else R.drawable.ic_play_arrow_black_24dp)
                    }
                }

            Timber.i("Is playing ${playbackManager.isPlaying()}")

            // Tell the AppWidgetManager to perform an update on the current app widget
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    private fun playbackPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, PlaybackService::class.java).apply {
            action = PlaybackService.ACTION_TOGGLE_PLAYBACK
        }
        return PendingIntent.getService(context, 1, intent, 0)
    }

    private fun nextPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, PlaybackService::class.java).apply {
            action = PlaybackService.ACTION_SKIP_NEXT
        }
        return PendingIntent.getService(context, 1, intent, 0)
    }
}