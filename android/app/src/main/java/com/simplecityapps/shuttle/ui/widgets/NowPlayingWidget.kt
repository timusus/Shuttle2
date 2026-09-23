package com.simplecityapps.shuttle.ui.widgets

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartService
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.components.CircleIconButton
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.simplecityapps.core.R as CoreR
import com.simplecityapps.playback.PlaybackService
import com.simplecityapps.playback.R as PlaybackR
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.MainActivity
import com.simplecityapps.shuttle.ui.theme.ShuttleTheme

/**
 * The now playing widget, shared by the small and large widget receivers. It only draws
 * [NowPlayingWidgetState]; [WidgetManager] writes that state and asks for a redraw.
 */
class NowPlayingWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(widgetBreakpoints)

    override val stateDefinition = NowPlayingWidgetStateDefinition

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId
    ) {
        provideContent {
            GlanceTheme(colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) GlanceTheme.colors else fallbackColors) {
                NowPlayingContent(
                    state = currentState(),
                    layout = widgetLayoutFor(LocalSize.current)
                )
            }
        }
    }
}

/**
 * The widget palette where dynamic colour isn't available, taken from the app's own theme. The accent is
 * adjusted in both modes: the app's primary blues are tuned for filled surfaces, and as icon tints on the
 * widget background they fall short of 3:1 contrast.
 */
private val fallbackColors =
    ColorProviders(
        light = ShuttleTheme.light.copy(primary = Color(0xFF0061A4)),
        dark = ShuttleTheme.dark.copy(primary = Color(0xFF9ECAFF))
    )

@Composable
private fun NowPlayingContent(
    state: NowPlayingWidgetState,
    layout: WidgetLayout
) {
    WidgetContainer(padding = layout.padding) {
        if (!state.hasTrack) {
            IdleContent(layout)
        } else {
            when (layout.mode) {
                WidgetMode.Row -> RowContent(state, layout)
                WidgetMode.Card -> CardContent(state, layout)
                WidgetMode.Large -> LargeContent(state, layout)
                WidgetMode.Tile -> TileContent(state, layout)
            }
        }
    }
}

@Composable
private fun WidgetContainer(
    padding: Dp,
    content: @Composable () -> Unit
) {
    val background =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            GlanceModifier
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(android.R.dimen.system_app_widget_background_radius)
        } else {
            GlanceModifier.background(
                imageProvider = ImageProvider(R.drawable.widget_background),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.widgetBackground)
            )
        }
    Box(
        modifier =
        GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .then(background)
            .clickable(actionStartActivity<MainActivity>())
            .padding(padding),
        contentAlignment = Alignment.CenterStart
    ) {
        content()
    }
}

// Layouts

@Composable
private fun RowContent(
    state: NowPlayingWidgetState,
    layout: WidgetLayout
) {
    Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        if (layout.showArt) {
            Artwork(state.artworkPath, layout.artSize)
            Spacer(GlanceModifier.width(WidgetDimens.gap))
        }
        if (layout.textLines == 0) {
            ButtonRow(layout.buttons, state)
        } else {
            TrackText(state, lines = layout.textLines, large = false, modifier = GlanceModifier.defaultWeight())
            layout.buttons.forEach { ControlButton(it, state) }
        }
    }
}

@Composable
private fun CardContent(
    state: NowPlayingWidgetState,
    layout: WidgetLayout
) {
    Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        if (layout.showArt) {
            Artwork(state.artworkPath, layout.artSize)
            Spacer(GlanceModifier.width(WidgetDimens.gap))
        }
        Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {
            TrackText(
                state,
                lines = layout.textLines,
                large = false,
                modifier = GlanceModifier.fillMaxWidth().defaultWeight().padding(start = 4.dp, end = 4.dp)
            )
            ButtonRow(layout.buttons, state)
        }
    }
}

@Composable
private fun LargeContent(
    state: NowPlayingWidgetState,
    layout: WidgetLayout
) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
            Artwork(state.artworkPath, layout.artSize)
            Spacer(GlanceModifier.width(WidgetDimens.gap + 4.dp))
            TrackText(state, lines = layout.textLines, large = layout.textLines >= 3, modifier = GlanceModifier.defaultWeight())
        }
        Spacer(GlanceModifier.height(WidgetDimens.gap))
        ButtonRow(layout.buttons, state)
    }
}

@Composable
private fun TileContent(
    state: NowPlayingWidgetState,
    layout: WidgetLayout
) {
    Column(modifier = GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = GlanceModifier.fillMaxWidth().defaultWeight(), contentAlignment = Alignment.Center) {
            Artwork(state.artworkPath, layout.artSize)
        }
        Spacer(GlanceModifier.height(WidgetDimens.gap))
        TrackText(state, lines = layout.textLines, large = false, modifier = GlanceModifier.fillMaxWidth().padding(horizontal = 4.dp))
        Spacer(GlanceModifier.height(WidgetDimens.gap))
        ButtonRow(layout.buttons, state)
    }
}

@Composable
private fun IdleContent(layout: WidgetLayout) {
    val context = LocalContext.current
    val compact = layout.mode == WidgetMode.Row
    val iconSize = if (compact) 32.dp else 48.dp
    val content: @Composable () -> Unit = {
        Image(
            provider = ImageProvider(CoreR.drawable.ic_shuttle_logo),
            contentDescription = null,
            modifier = GlanceModifier.size(iconSize)
        )
    }
    val text: @Composable (GlanceModifier, Boolean) -> Unit = { modifier, centred ->
        Column(modifier = modifier, horizontalAlignment = if (centred) Alignment.CenterHorizontally else Alignment.Start) {
            Text(
                text = context.getString(R.string.app_name),
                maxLines = 1,
                style = titleStyle(large = !compact)
            )
            Text(
                text = context.getString(R.string.widget_idle_action),
                maxLines = 1,
                style = subtitleStyle(large = !compact, color = GlanceTheme.colors.primary)
            )
        }
    }
    if (layout.mode == WidgetMode.Tile || layout.mode == WidgetMode.Large && layout.buttons.size < 5) {
        Column(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
            content()
            Spacer(GlanceModifier.height(WidgetDimens.gap))
            text(GlanceModifier, true)
        }
    } else {
        Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            content()
            Spacer(GlanceModifier.width(WidgetDimens.gap + 4.dp))
            text(GlanceModifier.defaultWeight(), false)
        }
    }
}

// Pieces

@Composable
private fun TrackText(
    state: NowPlayingWidgetState,
    lines: Int,
    large: Boolean,
    modifier: GlanceModifier
) {
    Column(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(text = state.title, maxLines = 1, style = titleStyle(large))
        if (state.artist.isNotEmpty()) {
            Text(text = state.artist, maxLines = 1, style = subtitleStyle(large))
        }
        if (lines >= 3 && state.album.isNotEmpty()) {
            Text(text = state.album, maxLines = 1, style = subtitleStyle(large))
        }
    }
}

@Composable
private fun titleStyle(large: Boolean) = TextStyle(
    color = GlanceTheme.colors.onSurface,
    fontSize = if (large) 16.sp else 14.sp,
    fontWeight = FontWeight.Medium
)

@Composable
private fun subtitleStyle(
    large: Boolean,
    color: ColorProvider = GlanceTheme.colors.onSurfaceVariant
) = TextStyle(
    color = color,
    fontSize = if (large) 14.sp else 12.sp
)

@Composable
private fun Artwork(
    path: String?,
    size: Dp
) {
    val context = LocalContext.current
    val bitmap =
        path?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                WidgetArtworkBitmaps.full(it)
            } else {
                val density = context.resources.displayMetrics.density
                WidgetArtworkBitmaps.rounded(it, (size.value * density).toInt(), context.resources.getDimension(R.dimen.widget_inner_radius))
            }
        }
    if (bitmap != null) {
        Image(
            provider = ImageProvider(bitmap),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = GlanceModifier.size(size).innerCornerRadius()
        )
    } else {
        ArtworkPlaceholder(size)
    }
}

/** Missing artwork: a music note on the theme's secondary container, shaped like the artwork it stands in for. */
@Composable
private fun ArtworkPlaceholder(size: Dp) {
    val background =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            GlanceModifier.background(GlanceTheme.colors.secondaryContainer).innerCornerRadius()
        } else {
            GlanceModifier.background(
                imageProvider = ImageProvider(R.drawable.widget_inner_background),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.secondaryContainer)
            )
        }
    Box(modifier = GlanceModifier.size(size).then(background), contentAlignment = Alignment.Center) {
        Image(
            provider = ImageProvider(R.drawable.widget_music_note),
            contentDescription = null,
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onSecondaryContainer),
            modifier = GlanceModifier.size(min(size * 0.4f, 64.dp))
        )
    }
}

private fun GlanceModifier.innerCornerRadius(): GlanceModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) cornerRadius(R.dimen.widget_inner_radius) else this

/** Buttons spread evenly across the available width, so play/pause sits in the middle. */
@Composable
private fun ButtonRow(
    buttons: List<WidgetButton>,
    state: NowPlayingWidgetState
) {
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        buttons.forEach { button ->
            Box(modifier = GlanceModifier.defaultWeight(), contentAlignment = Alignment.Center) {
                ControlButton(button, state)
            }
        }
    }
}

@Composable
private fun ControlButton(
    button: WidgetButton,
    state: NowPlayingWidgetState
) {
    val context = LocalContext.current
    val colors = GlanceTheme.colors
    when (button) {
        WidgetButton.PlayPause ->
            CircleIconButton(
                imageProvider = ImageProvider(if (state.isPlaying) PlaybackR.drawable.ic_pause_black_24dp else PlaybackR.drawable.ic_play_arrow_black_24dp),
                contentDescription = context.getString(if (state.isPlaying) R.string.widget_pause else R.string.widget_play),
                onClick = playbackAction(context, PlaybackService.ACTION_TOGGLE_PLAYBACK),
                backgroundColor = colors.primaryContainer,
                contentColor = colors.onPrimaryContainer
            )
        WidgetButton.Previous ->
            CircleIconButton(
                imageProvider = ImageProvider(PlaybackR.drawable.ic_skip_previous_black_24dp),
                contentDescription = context.getString(R.string.button_skip_previous),
                onClick = playbackAction(context, PlaybackService.ACTION_SKIP_PREV),
                backgroundColor = null,
                contentColor = colors.onSurfaceVariant
            )
        WidgetButton.Next ->
            CircleIconButton(
                imageProvider = ImageProvider(PlaybackR.drawable.ic_skip_next_black_24dp),
                contentDescription = context.getString(R.string.button_skip_next),
                onClick = playbackAction(context, PlaybackService.ACTION_SKIP_NEXT),
                backgroundColor = null,
                contentColor = colors.onSurfaceVariant
            )
        WidgetButton.Shuffle ->
            ToggleButton(
                icon = PlaybackR.drawable.ic_shuffle_black_24dp,
                contentDescription = context.getString(if (state.shuffleOn) CoreR.string.shuffle_on else CoreR.string.shuffle_off),
                onClick = playbackAction(context, PlaybackService.ACTION_TOGGLE_SHUFFLE),
                on = state.shuffleOn
            )
        WidgetButton.Repeat ->
            ToggleButton(
                icon = if (state.repeatMode == WidgetRepeatMode.One) R.drawable.ic_repeat_one_black_24dp else R.drawable.ic_repeat_black_24dp,
                contentDescription =
                context.getString(
                    when (state.repeatMode) {
                        WidgetRepeatMode.Off -> R.string.widget_repeat_off
                        WidgetRepeatMode.All -> R.string.widget_repeat_all
                        WidgetRepeatMode.One -> R.string.widget_repeat_one
                    }
                ),
                onClick = playbackAction(context, PlaybackService.ACTION_TOGGLE_REPEAT),
                on = state.repeatMode != WidgetRepeatMode.Off
            )
    }
}

/**
 * Shuffle or repeat. When on, the icon takes the primary colour and a dot sits beneath it: under some dynamic
 * palettes primary is close to the off colour, so the tint alone doesn't read at a glance.
 */
@Composable
private fun ToggleButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: Action,
    on: Boolean
) {
    val colors = GlanceTheme.colors
    Box(modifier = GlanceModifier.size(WidgetDimens.buttonSize), contentAlignment = Alignment.BottomCenter) {
        CircleIconButton(
            imageProvider = ImageProvider(icon),
            contentDescription = contentDescription,
            onClick = onClick,
            backgroundColor = null,
            contentColor = if (on) colors.primary else colors.onSurfaceVariant
        )
        if (on) {
            Box(modifier = GlanceModifier.padding(bottom = 5.dp)) {
                Image(
                    provider = ImageProvider(R.drawable.widget_toggle_dot),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(colors.primary),
                    modifier = GlanceModifier.size(4.dp)
                )
            }
        }
    }
}

private fun playbackAction(
    context: Context,
    action: String
): Action = actionStartService(Intent(context, PlaybackService::class.java).setAction(action), isForegroundService = true)
