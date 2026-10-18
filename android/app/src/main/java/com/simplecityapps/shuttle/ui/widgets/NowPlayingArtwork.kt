package com.simplecityapps.shuttle.ui.widgets

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.glance.ColorFilter
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
import androidx.glance.layout.Spacer
import androidx.glance.layout.height
import androidx.glance.layout.size
import com.simplecityapps.shuttle.R

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
internal fun artworkBitmap(
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
internal fun Artwork(
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
internal fun ArtworkPlaceholder(
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
