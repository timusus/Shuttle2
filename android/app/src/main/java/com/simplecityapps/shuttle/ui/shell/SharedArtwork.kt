package com.simplecityapps.shuttle.ui.shell

import android.provider.Settings
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import com.simplecityapps.shuttle.designsystem.component.SharedArtworkShape

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

fun albumArtworkKey(route: AlbumRoute): String = "artwork-album-${route.albumKey}|${route.albumArtistKey}|${route.albumIdentity}"

// The overlay draws the artwork outside its local clip, so it needs the rounded shape again while it flies.
@OptIn(ExperimentalSharedTransitionApi::class)
private val ArtworkOverlayClip = object : SharedTransitionScope.OverlayClip {
    override fun getClipPath(
        sharedContentState: SharedTransitionScope.SharedContentState,
        bounds: Rect,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Path = Path().apply {
        addOutline(SharedArtworkShape.createOutline(bounds.size, layoutDirection, density))
        translate(bounds.topLeft)
    }
}

/**
 * Shares this artwork between the entries of a Navigation 3 transition under [key]. A no-op outside the shell's
 * NavDisplay (previews, screenshot tests), where no scope is provided, and when [key] is null. [isEnabled] is read when
 * the elements are matched, so a source can opt out per transition (a duplicate of the same album on screen). With
 * system animations off the artwork isn't shared, leaving the destination transition to Navigation 3.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedArtwork(
    key: String?,
    isEnabled: () -> Boolean = { true },
): Modifier {
    if (key == null) return this
    val transition = LocalSharedTransitionScope.current ?: return this
    val visibility = LocalNavAnimatedContentScope.current
    val animationsOn = rememberAnimationsEnabled()
    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Rect>()
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
            boundsTransform = BoundsTransform { _, _ -> spatialSpec },
            clipInOverlayDuringTransition = ArtworkOverlayClip,
        )
    }
}

/** False when the system animator duration scale is 0 ("Remove animations" or the developer option). */
@Composable
private fun rememberAnimationsEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f }
}
