package com.simplecityapps.shuttle.ui.screens.library.albumartists.detail

import com.simplecityapps.shuttle.designsystem.theme.ArtworkSeed
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.AlbumGroupKey
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.common.PendingEvent

data class AlbumArtistDetailUiState(
    val albumArtist: AlbumArtist? = null,
    val albums: List<Album> = emptyList(),
    val songs: List<Song> = emptyList(),
    val currentSong: Song? = null,
    /** Albums whose track list is unfolded in place, keyed the same way songs are grouped. */
    val expandedAlbums: Set<AlbumGroupKey> = emptySet(),
    val loadingState: LoadingState = LoadingState.Loading,
    val events: List<PendingEvent<AlbumArtistDetailEvent>> = emptyList(),
    /** The newest album's artwork seed, which tints the screen when Colour from artwork is on. */
    val seed: ArtworkSeed = ArtworkSeed.None,
) {
    enum class LoadingState { Loading, Ready, Empty }

    /** The artist's songs belonging to [album], in track order. */
    fun songsForAlbum(album: Album): List<Song> = album.groupKey?.let { key ->
        songs.filter { it.albumGroupKey == key }
    }.orEmpty()
}

sealed interface AlbumArtistDetailEvent {
    /** Shuffle albums couldn't start playback; [reason] is the player's error, if it gave one. */
    data class ShuffleAlbumsFailed(val reason: String?) : AlbumArtistDetailEvent
}
