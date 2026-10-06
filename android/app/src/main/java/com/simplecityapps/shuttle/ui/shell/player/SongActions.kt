package com.simplecityapps.shuttle.ui.shell.player

import androidx.compose.runtime.staticCompositionLocalOf
import com.simplecityapps.shuttle.designsystem.component.ArtworkPlaceholder
import com.simplecityapps.shuttle.designsystem.component.S2Action
import com.simplecityapps.shuttle.ui.actions.MediaActionType
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import com.simplecityapps.shuttle.ui.common.mediaactions.MediaActionsState
import com.simplecityapps.shuttle.ui.common.mediaactions.MediaActionsTarget

/** The player's song menus and the queue's save-to-playlist run on the shell's one [MediaActionsState] (provided by `AppShell`). */
internal val LocalPlayerMediaActions = staticCompositionLocalOf<MediaActionsState> { error("No MediaActionsState: the player is hosted by AppShell") }

/**
 * The shared actions that make sense on the playing song or a queue row: not Play, Shuffle or the queue verbs,
 * which would rebuild or duplicate the queue it is already in (redesign inventory, section 4).
 */
internal val PlayerSongActions = setOf(
    MediaActionType.AddToPlaylist,
    MediaActionType.GoToAlbum,
    MediaActionType.GoToArtist,
    MediaActionType.EditTags,
    MediaActionType.SongInfo,
    MediaActionType.Exclude,
)

/** This song's actions sheet: the queue's own [leadingActions] (Play next, Remove), the [PlayerSongActions], then [extraActions] (Clear queue). */
internal fun PlayerSong.actionsTarget(
    leadingActions: List<S2Action> = emptyList(),
    extraActions: List<S2Action> = emptyList(),
) = MediaActionsTarget(
    title = title,
    subtitle = artist,
    selection = MediaSelection.Songs(song),
    placeholder = ArtworkPlaceholder.Song,
    extraActions = extraActions,
    leadingActions = leadingActions,
    onlyTypes = PlayerSongActions,
    artwork = { SongArtwork(song) },
)
