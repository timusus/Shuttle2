package com.simplecityapps.shuttle.ui.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import android.widget.RemoteViews
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import au.com.simplecityapps.shuttle.imageloading.glide.GlideImageLoader
import com.simplecityapps.mediaprovider.model.Song
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
    @Inject lateinit var artworkCache: HashMap<Song, Bitmap?>

    private var updateReason = WidgetManager.UpdateReason.Unknown

    override fun onReceive(context: Context?, intent: Intent?) {

        AndroidInjection.inject(this, context)

        updateReason = WidgetManager.UpdateReason.values()[intent?.extras?.getInt(WidgetManager.ARG_UPDATE_REASON) ?: WidgetManager.UpdateReason.Unknown.ordinal]

        Timber.i("onReceive intent: $intent, data: ${intent?.data?.toString()}, updateReason: $updateReason")

        super.onReceive(context, intent)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)

        Timber.i("onUpdate(), ids: $appWidgetIds")

        val imageLoader = GlideImageLoader(context)

        appWidgetIds.forEach { appWidgetId ->

            val contentIntent: PendingIntent = (context.applicationContext as ActivityIntentProvider).provideMainActivityIntent()
                .let { intent -> PendingIntent.getActivity(context, 0, intent, 0) }

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
                            val song = currentItem.song

                            setTextViewText(R.id.title, song.name)
                            setTextViewText(R.id.subtitle, "${song.albumArtistName} • ${song.albumName}")

                            artworkCache[song]?.let { image ->
                                setImageViewBitmap(R.id.artwork, image)
                            } ?: run {
                                setImageViewResource(R.id.artwork, R.drawable.ic_music_note_black_24dp)

                                imageLoader.loadBitmap(song, 48.dp, 48.dp, ArtworkImageLoader.Options.RoundedCorners(4.dp)) { image ->
                                    artworkCache[song] = image

                                    if (song == queueManager.getCurrentItem()?.song) {
                                        image?.let {
                                            setImageViewBitmap(R.id.artwork, image)
                                        } ?: run {
                                            setImageViewResource(R.id.artwork, R.drawable.ic_music_note_black_24dp)
                                        }
                                        appWidgetManager.updateAppWidget(appWidgetId, this)
                                    }
                                }
                            }

                            // Load the next song's artwork as well
                            queueManager.getNext(true)?.song?.let { song ->
                                artworkCache[song] ?: imageLoader.loadBitmap(song, 48.dp, 48.dp, ArtworkImageLoader.Options.RoundedCorners(4.dp)) { image ->
                                    artworkCache[song] = image
                                }
                            }
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