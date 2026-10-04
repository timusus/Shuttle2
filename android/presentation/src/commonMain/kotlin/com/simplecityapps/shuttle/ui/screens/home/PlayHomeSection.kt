package com.simplecityapps.shuttle.ui.screens.home

import com.simplecityapps.shuttle.ui.actions.MediaAction
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import com.simplecityapps.shuttle.ui.actions.ResolveSongs
import dev.zacsweers.metro.Inject

/**
 * The play action for a whole Home shelf: every song of its items in shelf order, each item's songs in its own order,
 * to replace the queue and start at the first. Null when the shelf has no songs, so there's nothing to do. The
 * [MediaAction] is dispatched like any other, so a failure to play is reported the same way.
 */
class PlayHomeSection @Inject constructor(
    private val resolveSongs: ResolveSongs,
) {
    suspend operator fun invoke(section: HomeSection): MediaAction? {
        val songs = section.items.flatMap { resolveSongs(it.playAction().selection) }
        return songs.takeIf { it.isNotEmpty() }?.let { MediaAction.Play(MediaSelection.Songs(it)) }
    }
}
