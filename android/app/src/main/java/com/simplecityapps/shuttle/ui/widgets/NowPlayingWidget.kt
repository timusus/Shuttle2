package com.simplecityapps.shuttle.ui.widgets

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
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
import androidx.glance.color.ColorProvider
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
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.simplecityapps.core.R as CoreR
import com.simplecityapps.playback.PlaybackService
import com.simplecityapps.playback.R as PlaybackR
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.theme.S2Accent
import com.simplecityapps.shuttle.designsystem.theme.accentColorScheme
import com.simplecityapps.shuttle.ui.MainActivity

/**
 * The now playing widget, shared by the small and large widget receivers. It only draws
 * [NowPlayingWidgetState]; [WidgetManager] writes that state and asks for a redraw.
 */
class NowPlayingWidget : GlanceAppWidget() {
    // Exact rather than responsive: the artwork is sized to fill the space up to the padding, which only
    // works when the layout knows the widget's real size rather than the nearest breakpoint below it.
    override val sizeMode = SizeMode.Exact

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

/** The widget palette where dynamic colour isn't available: the app's Shuttle blue scheme. */
private val fallbackColors =
    ColorProviders(
        light = accentColorScheme(S2Accent.Default, isDark = false),
        dark = accentColorScheme(S2Accent.Default, isDark = true)
    )

/** Visible to tests, which render it directly with a fixed [state] and [layout] instead of the live widget state. */
@Composable
internal fun NowPlayingContent(
    state: NowPlayingWidgetState,
    layout: WidgetLayout
) {
    // The hero's art fills the widget to its edges, so it insets its own text and buttons instead.
    val fullBleed = state.hasTrack && layout.mode == WidgetMode.Hero
    WidgetContainer(
        padding = if (fullBleed) 0.dp else layout.padding,
        bottomPadding = if (fullBleed) 0.dp else layout.bottomPadding,
        opacity = state.backgroundOpacity
    ) {
        if (!state.hasTrack) {
            IdleContent(layout)
        } else {
            when (layout.mode) {
                WidgetMode.Row -> RowContent(state, layout)
                WidgetMode.Card -> CardContent(state, layout)
                WidgetMode.Split -> SplitContent(state, layout)
                WidgetMode.Hero -> HeroContent(state, layout)
            }
        }
    }
}

/**
 * The widget's background, rounded to the launcher's radius, with [padding] on every side of [content] but the
 * bottom, which has [bottomPadding]. The content starts at the top left, so nothing is centred away from the
 * edge it's anchored to.
 */
@Composable
private fun WidgetContainer(
    padding: Dp,
    bottomPadding: Dp,
    opacity: Int,
    content: @Composable () -> Unit
) {
    val background =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            GlanceModifier
                .background(backgroundColor(opacity))
                .cornerRadius(android.R.dimen.system_app_widget_background_radius)
        } else {
            GlanceModifier
        }
    Box(
        modifier =
            GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .then(background)
                .clickable(actionStartActivity<MainActivity>())
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            // Views can't be clipped below API 31, so the rounded background is a drawable. A background
            // modifier can't be translucent, but an image can.
            Image(
                provider = ImageProvider(R.drawable.widget_background),
                contentDescription = null,
                alpha = widgetBackgroundAlpha(opacity),
                modifier = GlanceModifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
                colorFilter = ColorFilter.tint(GlanceTheme.colors.widgetBackground)
            )
        }
        Box(
            modifier = GlanceModifier.fillMaxSize().padding(start = padding, top = padding, end = padding, bottom = bottomPadding),
            contentAlignment = Alignment.TopStart
        ) {
            content()
        }
    }
}

/**
 * The theme's widget background at the opacity setting. Glance can't add alpha to a theme colour, so below
 * full opacity the theme colour is resolved here for light and dark, and the launcher picks between them as
 * the system switches, just as it does for the theme colour itself.
 */
@Composable
private fun backgroundColor(opacity: Int): ColorProvider {
    val base = GlanceTheme.colors.widgetBackground
    val alpha = widgetBackgroundAlpha(opacity)
    if (alpha >= 1f) return base
    val context = LocalContext.current
    return ColorProvider(
        day = base.getColor(context.withNightMode(false)).copy(alpha = alpha),
        night = base.getColor(context.withNightMode(true)).copy(alpha = alpha)
    )
}

private fun Context.withNightMode(night: Boolean): Context {
    val configuration = Configuration(resources.configuration)
    configuration.uiMode = configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv() or
        if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
    return createConfigurationContext(configuration)
}

// Layouts

@Composable
private fun RowContent(
    state: NowPlayingWidgetState,
    layout: WidgetLayout
) {
    Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        if (layout.showArt) {
            Artwork(state.artworkPath, layout.art, layout.padding)
            Spacer(GlanceModifier.width(layout.padding))
        }
        if (layout.textLines == 0) {
            ButtonRow(layout.buttons, state)
        } else {
            TrackText(state, layout, modifier = GlanceModifier.defaultWeight())
            // Keeps an ellipsised title off the play button's circle.
            Spacer(GlanceModifier.width(WidgetDimens.gap))
            layout.buttons.forEach { ControlButton(it, state) }
        }
    }
}

@Composable
private fun CardContent(
    state: NowPlayingWidgetState,
    layout: WidgetLayout
) {
    Row(modifier = GlanceModifier.fillMaxSize()) {
        if (layout.showArt) {
            Artwork(state.artworkPath, layout.art, layout.padding)
            Spacer(GlanceModifier.width(layout.padding))
        }
        // Text above the button row, never stacked with it, so the two can't overlap; compactCardMinHeight
        // guarantees there's room for both.
        Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {
            TrackText(state, layout, modifier = GlanceModifier.fillMaxWidth().defaultWeight())
            ButtonRow(layout.buttons, state, compact = layout.compact)
        }
    }
}

@Composable
private fun SplitContent(
    state: NowPlayingWidgetState,
    layout: WidgetLayout
) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
            Artwork(state.artworkPath, layout.art, layout.padding)
            Spacer(GlanceModifier.width(layout.padding))
            TrackText(state, layout, modifier = GlanceModifier.defaultWeight())
        }
        Spacer(GlanceModifier.height(WidgetDimens.gap))
        ButtonRow(layout.buttons, state)
    }
}

/**
 * The artwork fills the whole widget, clipped to its rounded corners, with the text and buttons along its
 * bottom, inset by the padding like every other layout's content. Over artwork they sit on a dark scrim in
 * white, so they read over any picture; over the placeholder they use its theme colours instead.
 *
 * The art and its placeholder stay opaque whatever the background opacity setting: they're content rather
 * than background, fading them would muddy the picture and the scrim's contrast, and with them covering the
 * widget there's no background left to show.
 */
@Composable
private fun HeroContent(
    state: NowPlayingWidgetState,
    layout: WidgetLayout
) {
    val bitmap = artworkBitmap(state.artworkPath, layout.art, padding = 0.dp)
    val colors = if (bitmap != null) ContentColors.onArt() else ContentColors.onPlaceholder()
    val clip = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) GlanceModifier.cornerRadius(android.R.dimen.system_app_widget_background_radius) else GlanceModifier
    Box(modifier = GlanceModifier.fillMaxSize().then(clip)) {
        if (bitmap != null) {
            Image(
                provider = ImageProvider(bitmap),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = GlanceModifier.fillMaxSize()
            )
        } else {
            ArtworkPlaceholder(layout.art, padding = 0.dp, noteAlignment = Alignment.TopCenter)
        }
        Column(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.Bottom) {
            val scrim = bitmap != null
            if (scrim) {
                Spacer(GlanceModifier.fillMaxWidth().height(WidgetDimens.scrimFade).background(ImageProvider(R.drawable.widget_scrim_fade)))
            }
            Column(
                modifier =
                    GlanceModifier
                        .fillMaxWidth()
                        .then(if (scrim) GlanceModifier.background(ImageProvider(R.drawable.widget_scrim)) else GlanceModifier)
                        .padding(start = layout.padding, end = layout.padding, bottom = layout.padding)
            ) {
                TrackText(state, layout, colors = colors, modifier = GlanceModifier.fillMaxWidth())
                Spacer(GlanceModifier.height(WidgetDimens.gap / 2))
                ButtonRow(layout.buttons, state, colors)
            }
        }
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
                style = titleStyle(large = !compact, color = GlanceTheme.colors.onSurface)
            )
            Text(
                text = context.getString(R.string.widget_idle_action),
                maxLines = 1,
                style = subtitleStyle(large = !compact, color = GlanceTheme.colors.primary)
            )
        }
    }
    if (layout.mode == WidgetMode.Hero) {
        Column(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
            content()
            Spacer(GlanceModifier.height(WidgetDimens.gap))
            text(GlanceModifier, true)
        }
    } else {
        Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            content()
            Spacer(GlanceModifier.width(layout.padding))
            text(GlanceModifier.defaultWeight(), false)
        }
    }
}

// Pieces

/** Colours for text and controls, which change when they're drawn over artwork. */
private data class ContentColors(
    val title: ColorProvider,
    val subtitle: ColorProvider,
    val icon: ColorProvider,
    val toggleOff: ColorProvider,
    val toggleOn: ColorProvider,
    val playBackground: ColorProvider,
    val playContent: ColorProvider
) {
    companion object {
        @Composable
        fun default() = GlanceTheme.colors.let {
            ContentColors(
                title = it.onSurface,
                subtitle = it.onSurfaceVariant,
                icon = it.onSurfaceVariant,
                toggleOff = it.onSurfaceVariant,
                toggleOn = it.primary,
                playBackground = it.primaryContainer,
                playContent = it.onPrimaryContainer
            )
        }

        /** Over the scrim: white, with toggles that are off dimmed, so the dot isn't the only cue. */
        @Composable
        fun onArt() = GlanceTheme.colors.let {
            ContentColors(
                title = ColorProvider(Color.White),
                subtitle = ColorProvider(Color.White.copy(alpha = 0.85f)),
                icon = ColorProvider(Color.White),
                toggleOff = ColorProvider(Color.White.copy(alpha = 0.7f)),
                toggleOn = ColorProvider(Color.White),
                playBackground = it.primaryContainer,
                playContent = it.onPrimaryContainer
            )
        }

        @Composable
        fun onPlaceholder() = GlanceTheme.colors.let {
            ContentColors(
                title = it.onSecondaryContainer,
                subtitle = it.onSecondaryContainer,
                icon = it.onSecondaryContainer,
                toggleOff = it.onSecondaryContainer,
                toggleOn = it.primary,
                // primaryContainer is too close to the placeholder's secondaryContainer to read as a button.
                playBackground = it.primary,
                playContent = it.onPrimary
            )
        }
    }
}

@Composable
private fun TrackText(
    state: NowPlayingWidgetState,
    layout: WidgetLayout,
    modifier: GlanceModifier,
    colors: ContentColors = ContentColors.default()
) {
    // Beside art that fills the height the text centres on it; everywhere else it starts at the top.
    val alignment = if (layout.mode == WidgetMode.Row) Alignment.CenterVertically else Alignment.Top
    Column(modifier = modifier, verticalAlignment = alignment) {
        if (layout.textLines == 1) {
            // A compact card too short for two lines runs the artist on after the title.
            val text = listOf(state.title, state.artist).filter { it.isNotEmpty() }.joinToString(" · ")
            Text(text = text, maxLines = 1, style = titleStyle(layout.largeText, colors.title))
        } else {
            Text(text = state.title, maxLines = layout.titleLines, style = titleStyle(layout.largeText, colors.title))
            if (state.artist.isNotEmpty()) {
                Text(text = state.artist, maxLines = 1, style = subtitleStyle(layout.largeText, colors.subtitle))
            }
        }
        if (layout.textLines >= 3 && state.album.isNotEmpty()) {
            Text(text = state.album, maxLines = 1, style = subtitleStyle(layout.largeText, colors.subtitle))
        }
    }
}

private fun titleStyle(
    large: Boolean,
    color: ColorProvider
) = TextStyle(
    color = color,
    fontSize = if (large) 16.sp else 14.sp,
    fontWeight = FontWeight.Medium
)

private fun subtitleStyle(
    large: Boolean,
    color: ColorProvider
) = TextStyle(
    color = color,
    fontSize = if (large) 14.sp else 12.sp
)

/** The artwork's corner radius, concentric with the widget's for the given [padding]. */
@Composable
private fun innerRadius(padding: Dp): Dp {
    val resources = LocalContext.current.resources
    val outer = (resources.getDimension(R.dimen.widget_background_radius) / resources.displayMetrics.density).dp
    return innerCornerRadius(outer, padding)
}

/**
 * The artwork decoded for [size]. Below API 31 its corners are drawn into the bitmap, since the widget can't
 * clip them; above, the widget does.
 */
@Composable
private fun artworkBitmap(
    path: String?,
    size: DpSize,
    padding: Dp
): Bitmap? {
    path ?: return null
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    val widthPx = (size.width.value * density).toInt()
    val heightPx = (size.height.value * density).toInt()
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        WidgetArtworkBitmaps.sized(path, widthPx, heightPx)
    } else {
        WidgetArtworkBitmaps.rounded(path, widthPx, heightPx, innerRadius(padding).value * density)
    }
}

@Composable
private fun Artwork(
    path: String?,
    size: DpSize,
    padding: Dp
) {
    val bitmap = artworkBitmap(path, size, padding)
    if (bitmap != null) {
        Image(
            provider = ImageProvider(bitmap),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = GlanceModifier.size(size.width, size.height).innerCornerRadius(padding)
        )
    } else {
        ArtworkPlaceholder(size, padding)
    }
}

/**
 * Missing artwork: a music note on the theme's secondary container, shaped like the artwork it stands in for.
 * A [padding] of zero means it fills the widget, so it takes the widget's own corners.
 */
@Composable
private fun ArtworkPlaceholder(
    size: DpSize,
    padding: Dp,
    noteAlignment: Alignment = Alignment.Center
) {
    val background =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            GlanceModifier.background(GlanceTheme.colors.secondaryContainer).innerCornerRadius(padding)
        } else {
            GlanceModifier.background(
                imageProvider = ImageProvider(if (padding == 0.dp) R.drawable.widget_background else R.drawable.widget_inner_background),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.secondaryContainer)
            )
        }
    val noteSize = min(min(size.width, size.height) * 0.4f, 64.dp)
    // In the hero the text covers the bottom, so the note centres in what's left above it. The offset is a
    // spacer rather than padding, which in a widget eats into the image's own size.
    val noteOffset = if (noteAlignment == Alignment.TopCenter) ((size.height - HERO_TEXT_HEIGHT - noteSize) / 2).coerceAtLeast(0.dp) else 0.dp
    Box(modifier = GlanceModifier.size(size.width, size.height).then(background), contentAlignment = noteAlignment) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (noteOffset > 0.dp) Spacer(GlanceModifier.height(noteOffset))
            Image(
                provider = ImageProvider(R.drawable.widget_music_note),
                contentDescription = null,
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onSecondaryContainer),
                modifier = GlanceModifier.size(noteSize)
            )
        }
    }
}

/** The text and buttons along the bottom of the hero, with their padding, so its placeholder note can centre above them. */
private val HERO_TEXT_HEIGHT = WidgetDimens.titleLineHeight + WidgetDimens.subtitleLineHeight + WidgetDimens.gap / 2 + WidgetDimens.buttonSize + WidgetDimens.padding

@Composable
private fun GlanceModifier.innerCornerRadius(padding: Dp): GlanceModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) cornerRadius(innerRadius(padding)) else this

/**
 * Buttons spread evenly across the available width, so play/pause sits in the middle. When [compact], the play
 * button's circle is [WidgetDimens.compactPlay] inside its full-size target.
 */
@Composable
private fun ButtonRow(
    buttons: List<WidgetButton>,
    state: NowPlayingWidgetState,
    colors: ContentColors = ContentColors.default(),
    compact: Boolean = false
) {
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        buttons.forEach { button ->
            Box(modifier = GlanceModifier.defaultWeight(), contentAlignment = Alignment.Center) {
                if (compact && button == WidgetButton.PlayPause) {
                    CompactPlayButton(state, colors)
                } else {
                    ControlButton(button, state, colors)
                }
            }
        }
    }
}

/** Play/pause with a smaller circle than [CircleIconButton] draws, in the same 48dp touch target. */
@Composable
private fun CompactPlayButton(
    state: NowPlayingWidgetState,
    colors: ContentColors
) {
    val context = LocalContext.current
    val description = context.getString(if (state.isPlaying) R.string.widget_pause else R.string.widget_play)
    // Rounding the target keeps its ripple a circle rather than a square around the smaller one.
    val round = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) GlanceModifier.cornerRadius(WidgetDimens.buttonSize / 2) else GlanceModifier
    Box(
        modifier =
            GlanceModifier
                .size(WidgetDimens.buttonSize)
                .then(round)
                .clickable(playbackAction(context, PlaybackService.ACTION_TOGGLE_PLAYBACK))
                .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Image(
            provider = ImageProvider(R.drawable.widget_toggle_dot),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colors.playBackground),
            modifier = GlanceModifier.size(WidgetDimens.compactPlay)
        )
        Image(
            provider = ImageProvider(if (state.isPlaying) PlaybackR.drawable.ic_pause_black_24dp else PlaybackR.drawable.ic_play_arrow_black_24dp),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colors.playContent),
            modifier = GlanceModifier.size(24.dp)
        )
    }
}

@Composable
private fun ControlButton(
    button: WidgetButton,
    state: NowPlayingWidgetState,
    colors: ContentColors = ContentColors.default()
) {
    val context = LocalContext.current
    when (button) {
        WidgetButton.PlayPause ->
            CircleIconButton(
                imageProvider = ImageProvider(if (state.isPlaying) PlaybackR.drawable.ic_pause_black_24dp else PlaybackR.drawable.ic_play_arrow_black_24dp),
                contentDescription = context.getString(if (state.isPlaying) R.string.widget_pause else R.string.widget_play),
                onClick = playbackAction(context, PlaybackService.ACTION_TOGGLE_PLAYBACK),
                backgroundColor = colors.playBackground,
                contentColor = colors.playContent
            )

        WidgetButton.Previous ->
            CircleIconButton(
                imageProvider = ImageProvider(PlaybackR.drawable.ic_skip_previous_black_24dp),
                contentDescription = context.getString(R.string.button_skip_previous),
                onClick = playbackAction(context, PlaybackService.ACTION_SKIP_PREV),
                backgroundColor = null,
                contentColor = colors.icon
            )

        WidgetButton.Next ->
            CircleIconButton(
                imageProvider = ImageProvider(PlaybackR.drawable.ic_skip_next_black_24dp),
                contentDescription = context.getString(R.string.button_skip_next),
                onClick = playbackAction(context, PlaybackService.ACTION_SKIP_NEXT),
                backgroundColor = null,
                contentColor = colors.icon
            )

        WidgetButton.Shuffle ->
            ToggleButton(
                icon = PlaybackR.drawable.ic_shuffle_black_24dp,
                contentDescription = context.getString(if (state.shuffleOn) CoreR.string.shuffle_on else CoreR.string.shuffle_off),
                onClick = playbackAction(context, PlaybackService.ACTION_TOGGLE_SHUFFLE),
                on = state.shuffleOn,
                colors = colors
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
                on = state.repeatMode != WidgetRepeatMode.Off,
                colors = colors
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
    on: Boolean,
    colors: ContentColors
) {
    Box(modifier = GlanceModifier.size(WidgetDimens.buttonSize), contentAlignment = Alignment.BottomCenter) {
        CircleIconButton(
            imageProvider = ImageProvider(icon),
            contentDescription = contentDescription,
            onClick = onClick,
            backgroundColor = null,
            contentColor = if (on) colors.toggleOn else colors.toggleOff
        )
        if (on) {
            Box(modifier = GlanceModifier.padding(bottom = 5.dp)) {
                Image(
                    provider = ImageProvider(R.drawable.widget_toggle_dot),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(colors.toggleOn),
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
