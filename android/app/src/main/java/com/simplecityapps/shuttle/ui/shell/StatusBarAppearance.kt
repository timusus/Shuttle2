package com.simplecityapps.shuttle.ui.shell

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.simplecityapps.shuttle.ui.shell.player.PlayerLevel

/**
 * Screens ask for how the status bar must look; the shell alone applies it ([ApplyStatusBarAppearance]).
 * The most recently composed request wins, which is the screen on top after a push.
 */
@Stable
class StatusBarRequests {
    private val requests = mutableStateListOf<Request>()

    class Request internal constructor() {
        var overImage by mutableStateOf(false)
    }

    /** True when the topmost screen draws an image behind the status bar. */
    val overImage: Boolean get() = requests.lastOrNull()?.overImage ?: false

    internal fun add(request: Request) {
        requests.add(request)
    }

    internal fun remove(request: Request) {
        requests.remove(request)
    }
}

/** Null outside the shell (previews, isolated screen tests), where nothing applies the request. */
val LocalStatusBarRequests = staticCompositionLocalOf<StatusBarRequests?> { null }

/** Publishes whether this screen has an image behind the status bar, for as long as it is composed. */
@Composable
fun RequestStatusBarOverImage(overImage: Boolean) {
    val requests = LocalStatusBarRequests.current ?: return
    val request = remember(requests) { StatusBarRequests.Request() }
    request.overImage = overImage
    DisposableEffect(requests, request) {
        requests.add(request)
        onDispose { requests.remove(request) }
    }
}

/**
 * Whether the status bar icons are dark (for a light bar). An expanded player draws its own surface, so it takes the
 * theme default whatever screen sits beneath it; otherwise an image behind the bar forces light icons.
 */
internal fun darkStatusBarIcons(
    playerLevel: PlayerLevel,
    overImage: Boolean,
    darkTheme: Boolean,
): Boolean {
    val themeDefault = !darkTheme
    return if (playerLevel == PlayerLevel.Full) themeDefault else themeDefault && !overImage
}

@Composable
internal fun ApplyStatusBarAppearance(
    requests: StatusBarRequests,
    playerLevel: PlayerLevel,
    darkTheme: Boolean,
) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val darkIcons = darkStatusBarIcons(playerLevel, requests.overImage, darkTheme)
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = darkIcons
    }
}
