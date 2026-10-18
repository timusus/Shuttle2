package com.simplecityapps.shuttle.shared.intents

import com.simplecityapps.mediaprovider.repository.playlists.PlaylistQuery
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.ui.actions.MediaAction
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.first

/** Builds the actions and playlists for App Intents here so Swift never spells out a query or play context. */
@Inject
class AppIntentLibrary(
    private val playlistRepository: PlaylistRepository,
) {
    /** Every provider's playlists as they are now. */
    suspend fun playlists(): List<Playlist> = playlistRepository.getPlaylists(PlaylistQuery.All(mediaProviderType = null)).first()

    /** Shuffles the whole library, resolved as it plays, as Home's Shuffle All does. */
    fun shuffleAll(): MediaAction = MediaAction.Shuffle(MediaSelection.SongsMatching(SongQuery.All()))

    /** Plays (or [shuffled], shuffles) the playlist with [id]; null when there's no such playlist any more. */
    suspend fun playPlaylist(id: Long, shuffled: Boolean): MediaAction? {
        val playlist = playlists().firstOrNull { it.id == id } ?: return null
        val selection = MediaSelection.Playlists(playlist)
        return if (shuffled) MediaAction.Shuffle(selection) else MediaAction.Play(selection)
    }
}
