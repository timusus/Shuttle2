package com.simplecityapps.shuttle.ui.widgets

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors

class Widget42 : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Access Hilt dependencies through EntryPoint
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java
        )

        provideContent {
            GlanceTheme {
                Widget42Content(
                    context = context,
                    playbackManager = entryPoint.playbackManager(),
                    queueManager = entryPoint.queueManager(),
                    preferenceManager = entryPoint.preferenceManager()
                )
            }
        }
    }

    @Composable
    private fun Widget42Content(
        context: Context,
        playbackManager: PlaybackManager,
        queueManager: QueueManager,
        preferenceManager: GeneralPreferenceManager
    ) {
        val currentItem = queueManager.getCurrentItem()
        val isDarkMode = preferenceManager.widgetDarkMode
        val backgroundAlpha = preferenceManager.widgetBackgroundTransparency / 100f

        val backgroundColor = if (isDarkMode) {
            Color.Black.copy(alpha = backgroundAlpha)
        } else {
            Color.White.copy(alpha = backgroundAlpha)
        }

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(backgroundColor)
                .cornerRadius(16.dp)
                .padding(12.dp)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Artwork
                Image(
                    provider = currentItem?.song?.let {
                        // TODO: Load actual artwork - Glance has limitations with async image loading
                        ImageProvider(if (isDarkMode) R.drawable.ic_music_note_white_24dp else com.simplecityapps.playback.R.drawable.ic_music_note_black_24dp)
                    } ?: ImageProvider(com.simplecityapps.core.R.drawable.ic_shuttle_logo),
                    contentDescription = "Album artwork",
                    modifier = GlanceModifier
                        .size(80.dp)
                        .cornerRadius(4.dp),
                    contentScale = ContentScale.Crop
                )

                Spacer(modifier = GlanceModifier.width(12.dp))

                // Song info
                Column(
                    modifier = GlanceModifier.defaultWeight()
                ) {
                    Text(
                        text = currentItem?.song?.name ?: context.getString(R.string.queue_empty),
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        maxLines = 1
                    )

                    Spacer(modifier = GlanceModifier.height(2.dp))

                    Text(
                        text = currentItem?.song?.let { song ->
                            song.friendlyArtistName ?: song.albumArtist ?: context.getString(com.simplecityapps.core.R.string.unknown)
                        } ?: context.getString(com.simplecityapps.core.R.string.widget_empty_text),
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 12.sp
                        ),
                        maxLines = 1
                    )

                    Spacer(modifier = GlanceModifier.height(2.dp))

                    Text(
                        text = currentItem?.song?.album ?: "",
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 12.sp
                        ),
                        maxLines = 1
                    )
                }
            }

            if (currentItem != null) {
                Spacer(modifier = GlanceModifier.height(8.dp))

                // Playback controls
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Previous button
                    Image(
                        provider = ImageProvider(if (isDarkMode) R.drawable.ic_skip_previous_white_24dp else R.drawable.ic_skip_previous_black_24dp),
                        contentDescription = "Previous",
                        modifier = GlanceModifier
                            .size(48.dp)
                            .clickable(actionRunCallback<PreviousTrackAction>())
                    )

                    // Play/Pause button
                    Image(
                        provider = ImageProvider(getPlayPauseIcon(playbackManager, isDarkMode)),
                        contentDescription = "Play/Pause",
                        modifier = GlanceModifier
                            .size(48.dp)
                            .clickable(actionRunCallback<PlayPauseAction>())
                    )

                    // Next button
                    Image(
                        provider = ImageProvider(if (isDarkMode) R.drawable.ic_skip_next_white_24dp else R.drawable.ic_skip_next_black_24dp),
                        contentDescription = "Next",
                        modifier = GlanceModifier
                            .size(48.dp)
                            .clickable(actionRunCallback<NextTrackAction>())
                    )
                }
            }
        }
    }

    private fun getPlayPauseIcon(playbackManager: PlaybackManager, isDarkMode: Boolean): Int {
        return when (playbackManager.playbackState()) {
            is PlaybackState.Loading, PlaybackState.Playing -> {
                if (isDarkMode) R.drawable.ic_pause_white_24dp else com.simplecityapps.playback.R.drawable.ic_pause_black_24dp
            }
            else -> {
                if (isDarkMode) R.drawable.ic_play_arrow_white_24dp else com.simplecityapps.playback.R.drawable.ic_play_arrow_black_24dp
            }
        }
    }
}

@AndroidEntryPoint
class WidgetProvider42 : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = Widget42()
}
