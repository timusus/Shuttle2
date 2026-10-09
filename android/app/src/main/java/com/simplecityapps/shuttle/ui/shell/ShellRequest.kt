package com.simplecityapps.shuttle.ui.shell

import androidx.navigation3.runtime.NavKey
import com.simplecityapps.shuttle.model.SmartPlaylistId
import com.simplecityapps.shuttle.ui.screens.library.SmartPlaylistRoute

/** An entry point's ask of the shell: show [tab], and [route] on top of its root when given. */
data class ShellRequest(val tab: ShellTab, val route: NavKey? = null) {

    companion object {
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
