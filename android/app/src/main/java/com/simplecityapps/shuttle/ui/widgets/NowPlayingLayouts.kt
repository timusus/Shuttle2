package com.simplecityapps.shuttle.ui.widgets

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
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
import androidx.glance.text.Text
import com.simplecityapps.core.R as CoreR
import com.simplecityapps.shuttle.R

@Composable
internal fun RowContent(
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
internal fun CardContent(
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
internal fun SplitContent(
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
internal fun HeroContent(
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
internal fun IdleContent(layout: WidgetLayout) {
    val context = LocalContext.current
    val compact = layout.mode == WidgetMode.Row
    val iconSize = if (compact) WidgetDimens.idleRowLogo else WidgetDimens.idleLogo
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
