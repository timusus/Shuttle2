package com.simplecityapps.shuttle.ui.shell

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

// Route keys for the Compose shell (docs/architecture/app-shell.md, section 4). Routes carry keys,
// never Parcelable models, and are @Serializable so the back stacks survive process death.

@Serializable
data object HomeRoute : NavKey

@Serializable
data object LibraryRoute : NavKey

@Serializable
data object SearchRoute : NavKey

/** Mirrors AlbumGroupKey: two nullable strings. */
@Serializable
data class AlbumRoute(
    val albumKey: String?,
    val albumArtistKey: String?,
) : NavKey

@Serializable
data object SettingsRoute : NavKey

/** A top-level destination with its own back stack, rooted at [root]. */
enum class ShellTab(
    val root: NavKey,
) {
    Home(HomeRoute),
    Library(LibraryRoute),
    Search(SearchRoute),
}
