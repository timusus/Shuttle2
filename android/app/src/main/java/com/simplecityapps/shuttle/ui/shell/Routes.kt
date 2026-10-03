package com.simplecityapps.shuttle.ui.shell

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

// Route keys for the Compose shell (docs/architecture/app-shell.md, section 4). Routes carry keys,
// never Parcelable models, and are @Serializable so the back stacks survive process death.

/**
 * A utility destination outside the three tabs, such as Settings and its screens: the shell shows it without the
 * navigation bar or rail, so no tab is lit, while the mini player stays docked at the bottom. It still opens on the
 * selected tab's stack, so back returns to that tab as it was.
 */
interface UtilityRoute : NavKey

@Serializable
data object HomeRoute : NavKey

@Serializable
data object LibraryRoute : NavKey

@Serializable
data object SearchRoute : NavKey

/** Mirrors AlbumGroupKey: three nullable strings (a back stack saved before the identity, #637, restores without one). */
@Serializable
data class AlbumRoute(
    val albumKey: String?,
    val albumArtistKey: String?,
    val albumIdentity: String? = null,
) : NavKey

@Serializable
data object SettingsRoute : UtilityRoute

/** The screen each tab's back stack starts at. */
val ShellTab.root: NavKey
    get() = when (this) {
        ShellTab.Home -> HomeRoute
        ShellTab.Library -> LibraryRoute
        ShellTab.Search -> SearchRoute
    }
