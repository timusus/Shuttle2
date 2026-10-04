package com.simplecityapps.shuttle.ui.screens.library.albumartists.detail

import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.AlbumGroupKey
import com.simplecityapps.shuttle.model.ArtistHeroArtwork
import com.simplecityapps.shuttle.model.PlayContext
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.model.playContext
import com.simplecityapps.shuttle.sorting.ArtistSongComparator
import com.simplecityapps.shuttle.sorting.ArtistSongSortOrder
import com.simplecityapps.shuttle.ui.common.PendingEvent
import com.simplecityapps.shuttle.ui.theme.ArtworkSeed

data class AlbumArtistDetailUiState(
    val albumArtist: AlbumArtist? = null,
    /** The artist's own albums (they're the album artist), newest first: the carousel's order, whatever [sortOrder] is. */
    val albums: List<Album> = emptyList(),
    /** Other album artists' albums with songs crediting them, compilations included, newest first; the section's hidden when empty. */
    val appearsOn: List<Album> = emptyList(),
    /** Every song in [sortOrder]'s visible order (their own albums' and those crediting them) across all [sections], collapsed ones included: the play order. */
    val songs: List<Song> = emptyList(),
    val sortOrder: ArtistSongSortOrder = ArtistSongSortOrder.Default,
    /**
     * The song list as shown: one section per album for the album orders, then any songs without one of the
     * artist's albums in a trailing section with no album; a single section with no album for the flat orders.
     */
    val sections: List<SongSection> = emptyList(),
    val currentSong: Song? = null,
    /** Albums whose track list is unfolded in place, keyed the same way songs are grouped. */
    val expandedAlbums: Set<AlbumGroupKey> = emptySet(),
    val loadingState: LoadingState = LoadingState.Loading,
    val events: List<PendingEvent<AlbumArtistDetailEvent>> = emptyList(),
    /** What the hero shows (#781): the artist's image if it's confidently theirs, else their top album's cover. */
    val hero: ArtistHeroArtwork? = null,
    /** The seed of the hero's image, which tints the screen when Colour from artwork is on; it never holds up the content, which shows at [ArtworkSeed.Loading] while it's extracted. */
    val seed: ArtworkSeed = ArtworkSeed.None,
) {
    /**
     * Whether the Albums shelf shows (#678): only while the song list has no album sections. Grouped by album, the
     * section headers are the albums (each opens its album), so a shelf above them would list them twice.
     */
    val showAlbumsShelf: Boolean get() = albums.isNotEmpty() && !hasAlbumSections

    /** Whether any section has an album: an album order can still resolve to nothing but the songs on none of them. */
    val hasAlbumSections: Boolean get() = sections.any { it.album != null }

    /** What playing this screen's songs starts the queue from (#633). */
    val playContext: PlayContext get() = albumArtist?.playContext ?: PlayContext.None

    enum class LoadingState { Loading, Ready, Empty }

    /** The artist's songs belonging to [album], in track order. */
    fun songsForAlbum(album: Album): List<Song> = album.groupKey?.let { key ->
        songs.filter { it.albumGroupKey == key }.sortedWith(ArtistSongComparator.trackOrder)
    }.orEmpty()

    /** A run of the song list: [album]'s songs in track order, or, with no album, songs listed flat or without an album. */
    data class SongSection(val album: Album?, val songs: List<Song>)
}

sealed interface AlbumArtistDetailEvent {
    /** Shuffle albums couldn't start playback; [reason] is the player's error, if it gave one. */
    data class ShuffleAlbumsFailed(val reason: String?) : AlbumArtistDetailEvent
}
