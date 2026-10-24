package com.simplecityapps.shuttle.ui.shell

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import com.simplecityapps.shuttle.designsystem.component.ArtworkShape
import com.simplecityapps.shuttle.designsystem.component.ArtworkSize
import com.simplecityapps.shuttle.designsystem.component.artworkShape

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/** False when the system animator duration scale is 0 ("Remove animations" or the developer option); provided by the shell. */
val LocalAnimationsEnabled = staticCompositionLocalOf { true }

fun albumArtworkKey(route: AlbumRoute): String = "artwork-album-${route.albumKey}|${route.albumArtistKey}|${route.albumIdentity}"

/** Reads the animator duration scale once and follows changes to it, unregistering its observer on leaving. */
@Composable
fun rememberAnimationsEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    var enabled by remember(resolver) { mutableStateOf(resolver.animationsEnabled()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                enabled = resolver.animationsEnabled()
            }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return enabled
}

private fun android.content.ContentResolver.animationsEnabled(): Boolean = Settings.Global.getFloat(this, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f

// The overlay draws the artwork outside its local clip, so it needs the element's own rounded shape again while it flies.
@OptIn(ExperimentalSharedTransitionApi::class)
private class ArtworkOverlayClip(private val shape: Shape) : SharedTransitionScope.OverlayClip {
    override fun getClipPath(
        sharedContentState: SharedTransitionScope.SharedContentState,
        bounds: Rect,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Path = Path().apply {
        addOutline(shape.createOutline(bounds.size, layoutDirection, density))
        translate(bounds.topLeft)
    }
}

/**
 * Shares this artwork between the entries of a Navigation 3 transition under [key]. A no-op outside the shell's
 * NavDisplay (previews, screenshot tests), where no scope is provided, and when [key] is null. [isEnabled] is read when
 * the elements are matched, so a source can opt out per transition (a duplicate of the same album on screen); pass a
 * remembered lambda. [size] is the artwork's own size, so the flying overlay is clipped to the same corners. With system
 * animations off the artwork isn't shared, leaving the destination transition to Navigation 3.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedArtwork(
    key: String?,
    size: ArtworkSize,
    isEnabled: () -> Boolean = AlwaysEnabled,
): Modifier {
    if (key == null) return this
    val transition = LocalSharedTransitionScope.current ?: return this
    val visibility = LocalNavAnimatedContentScope.current
    val animationsOn = LocalAnimationsEnabled.current
    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Rect>()
    val boundsTransform = remember(spatialSpec) { BoundsTransform { _, _ -> spatialSpec } }
    val shape = artworkShape(ArtworkShape.Rounded, size)
    val clip = remember(shape) { ArtworkOverlayClip(shape) }
    val config = remember(isEnabled, animationsOn) {
        object : SharedTransitionScope.SharedContentConfig {
            override val SharedTransitionScope.SharedContentState.isEnabled: Boolean
                get() = animationsOn && isEnabled()
        }
    }
    return with(transition) {
        this@sharedArtwork.sharedElement(
            sharedContentState = rememberSharedContentState(key, config),
            animatedVisibilityScope = visibility,
            boundsTransform = boundsTransform,
            clipInOverlayDuringTransition = clip,
        )
    }
}

private val AlwaysEnabled: () -> Boolean = { true }
