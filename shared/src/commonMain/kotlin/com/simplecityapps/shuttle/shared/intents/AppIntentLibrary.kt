package com.simplecityapps.shuttle.shared.intents

import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.ui.actions.MediaAction
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import com.simplecityapps.shuttle.ui.actions.ObservePlaylists
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.first

/**
 * What Siri, Shortcuts and Spotlight's App Intents (#758) play from the library: the playlists their playlist parameter
 * offers, and the [MediaAction]s Swift's `AppIntentPerformer` dispatches, built here so Swift never spells out a query or
 * a play context. The actions are the app's own (Home's Shuffle All, a playlist row's Play and Shuffle).
 */
@Inject
class AppIntentLibrary(
    private val observePlaylists: ObservePlaylists,
) {
    /** Every provider's playlists as they are now. */
    suspend fun playlists(): List<Playlist> = observePlaylists().first()

    /** Shuffles the whole library, resolved as it plays, as Home's Shuffle All does. */
    fun shuffleAll(): MediaAction = MediaAction.Shuffle(MediaSelection.SongsMatching(SongQuery.All()))

    /** Plays (or [shuffled], shuffles) the playlist with [id]; null when there's no such playlist any more. */
    suspend fun playPlaylist(id: Long, shuffled: Boolean): MediaAction? {
        val playlist = playlists().firstOrNull { it.id == id } ?: return null
        val selection = MediaSelection.Playlists(playlist)
        return if (shuffled) MediaAction.Shuffle(selection) else MediaAction.Play(selection)
    }
}
