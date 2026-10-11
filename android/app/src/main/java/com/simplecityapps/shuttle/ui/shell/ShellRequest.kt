package com.simplecityapps.shuttle.ui.shell

import androidx.navigation3.runtime.NavKey
import com.simplecityapps.shuttle.model.SmartPlaylistId
import com.simplecityapps.shuttle.ui.screens.library.SmartPlaylistRoute

/**
 * An entry point's ask of the shell: show [tab], and [route] on top of its root when given. With [openPlayer] the
 * destinations stay as they are and the player rises to Full instead.
 */
data class ShellRequest(val tab: ShellTab, val route: NavKey? = null, val openPlayer: Boolean = false) {

    companion object {
        /** Boolean intent extra that the media notification, the Now Playing widget and Cast set to open the full player. */
        const val EXTRA_OPEN_PLAYER = "com.simplecityapps.shuttle.extra.OPEN_PLAYER"

        val OpenPlayer = ShellRequest(ShellTab.Home, openPlayer = true)

        /** The request an intent's [EXTRA_OPEN_PLAYER] value makes of the shell, or null when it is absent or false. */
        fun fromOpenPlayerExtra(extra: Boolean): ShellRequest? = if (extra) OpenPlayer else null

        const val ACTION_OPEN_SEARCH = "com.simplecityapps.shuttle.shortcuts.OPEN_SEARCH"
        const val ACTION_OPEN_RECENTLY_PLAYED = "com.simplecityapps.shuttle.shortcuts.OPEN_RECENTLY_PLAYED"

        /** The request a launcher shortcut's intent [action] makes of the shell, or null for any other action. */
        fun fromShortcutAction(action: String?): ShellRequest? = when (action) {
            ACTION_OPEN_SEARCH -> ShellRequest(ShellTab.Search)
            ACTION_OPEN_RECENTLY_PLAYED -> ShellRequest(ShellTab.Home, SmartPlaylistRoute(SmartPlaylistId.History.id))
            else -> null
        }
    }
}
