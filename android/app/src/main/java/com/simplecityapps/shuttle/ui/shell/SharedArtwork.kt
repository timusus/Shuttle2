package com.simplecityapps.shuttle.ui.shell

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import com.simplecityapps.shuttle.ui.screens.library.AlbumArtistRoute

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

fun albumArtworkKey(route: AlbumRoute): String = "artwork-album-${route.albumKey}|${route.albumArtistKey}|${route.albumIdentity}"

fun artistArtworkKey(route: AlbumArtistRoute): String = "artwork-artist-${route.albumArtistKey}"

/**
 * Shares this artwork between the entries of a Navigation 3 transition under [key]. A no-op outside the shell's
 * NavDisplay (previews, screenshot tests), where no scope is provided, and when [key] is null.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedArtwork(key: String?): Modifier {
    if (key == null) return this
    val transition = LocalSharedTransitionScope.current ?: return this
    val visibility = LocalNavAnimatedContentScope.current
    return with(transition) {
        this@sharedArtwork.sharedElement(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = visibility,
        )
    }
}
