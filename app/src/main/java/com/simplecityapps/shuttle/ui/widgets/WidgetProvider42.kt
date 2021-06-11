package com.simplecityapps.shuttle.ui.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import com.simplecityapps.playback.getArtworkCacheKey
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.utils.dp

class WidgetProvider42 : ShuttleAppWidgetProvider() {

    override val layoutResIdLight: Int
        get() = R.layout.appwidget_42

    override val layoutResIdDark: Int
        get() = R.layout.appwidget_42_dark

    override fun RemoteViews.bind(
        context: Context,
        appWidgetId: Int,
        contentIntent: PendingIntent,
        imageLoader: ArtworkImageLoader,
        appWidgetManager: AppWidgetManager,
        backgroundTransparency: Float
    ) {
        setOnClickPendingIntent(R.id.container, contentIntent)
        setOnClickPendingIntent(R.id.playPauseButton, playbackPendingIntent(context))
        setOnClickPendingIntent(R.id.prevButton, prevPendingIntent(context))
        setOnClickPendingIntent(R.id.nextButton, nextPendingIntent(context))

        setInt(R.id.background, "setImageAlpha", (backgroundTransparency * 255f).toInt())

        queueManager.getCurrentItem()?.let { currentItem ->
            setViewVisibility(R.id.subtitle, View.VISIBLE)

            val song = currentItem.song

            setTextViewText(R.id.title, song.name)
            setTextViewText(R.id.subtitle, song.friendlyArtistName ?: song.albumArtist)
            setTextViewText(R.id.subtitle2, song.album)

            val artworkSize = 80.dp

            artworkCache[song.getArtworkCacheKey(artworkSize, artworkSize)]?.let { image ->
                setImageViewBitmap(R.id.artwork, image)
            } ?: run {
                setImageViewResource(R.id.artwork, getPlaceholderDrawable())

                imageLoader.loadBitmap(
                    song,
                    artworkSize,
                    artworkSize,
                    listOf(ArtworkImageLoader.Options.RoundedCorners(4.dp))
                ) { image ->
                    if (image != null) {
                        artworkCache.put(song.getArtworkCacheKey(artworkSize, artworkSize), image)
                    }

                    if (song == queueManager.getCurrentItem()?.song) {
                        image?.let {
                            setImageViewBitmap(R.id.artwork, image)
                        } ?: run {
                            setImageViewResource(R.id.artwork, getPlaceholderDrawable())
                        }
                        appWidgetManager.updateAppWidget(appWidgetId, this)
                    }
                }
            }

            // Load the next song's artwork as well
            queueManager.getNext(true)?.song?.let { song ->
                artworkCache[song.getArtworkCacheKey(artworkSize, artworkSize)] ?: imageLoader.loadBitmap(
                    song,
                    artworkSize,
                    artworkSize,
                    listOf(ArtworkImageLoader.Options.RoundedCorners(4.dp))
                ) { image ->
                    if (image != null) {
                        artworkCache.put(song.getArtworkCacheKey(artworkSize, artworkSize), image)
                    }
                }
            }
        } ?: run {
            setTextViewText(R.id.title, context.getString(R.string.widget_empty_text))
            setViewVisibility(R.id.subtitle, View.GONE)
        }

        if (updateReason == WidgetManager.UpdateReason.PlaystateChanged || updateReason == WidgetManager.UpdateReason.Unknown) {
            setImageViewResource(R.id.playPauseButton, getPlaybackDrawable())
        }
    }
}