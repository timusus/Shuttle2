package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.Genre
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.PlayContext
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.model.playContext
import com.simplecityapps.shuttle.query.SongQuery

/**
 * What a [MediaAction] acts on: one or more items of a single media type, from a row's actions or a multi-selection.
 * [ResolveSongs] turns any selection into the songs it stands for.
 */
sealed interface MediaSelection {
    data class Songs(val songs: List<Song>) : MediaSelection {
        constructor(song: Song) : this(listOf(song))
    }

    data class Albums(val albums: List<Album>) : MediaSelection {
        constructor(album: Album) : this(listOf(album))
    }

    data class AlbumArtists(val albumArtists: List<AlbumArtist>) : MediaSelection {
        constructor(albumArtist: AlbumArtist) : this(listOf(albumArtist))
    }

    data class Genres(val genres: List<Genre>) : MediaSelection {
        constructor(genre: Genre) : this(listOf(genre))
    }

    data class Playlists(val playlists: List<Playlist>) : MediaSelection {
        constructor(playlist: Playlist) : this(listOf(playlist))
    }

    /** Local library folders, each identified by its [com.simplecityapps.shuttle.model.SongFolder] path. */
    data class Folders(val paths: List<List<String>>) : MediaSelection

    /**
     * The songs matching [query] when the action runs, in its sort order: the whole library for Shuffle all, a built-in
     * smart playlist; resolved then, so nothing holds the song list before it's played (#633).
     */
    data class SongsMatching(val query: SongQuery) : MediaSelection

    /** The current play queue. */
    data object Queue : MediaSelection

    /** The providers the selection's items come from, where known without resolving its songs; null when not. */
    val mediaProviders: List<MediaProviderType>?
        get() = when (this) {
            is Songs -> songs.map { it.mediaProvider }.distinct()
            is Albums -> albums.flatMap { it.mediaProviders }.distinct()
            is AlbumArtists -> albumArtists.flatMap { it.mediaProviders }.distinct()
            is Genres -> genres.flatMap { it.mediaProviders }.distinct()
            is Playlists -> playlists.map { it.mediaProvider }.distinct()
            is SongsMatching, is Folders, is Queue -> null
        }

    /**
     * What playing the selection starts a queue from: a single album, artist, genre or playlist is that context; songs,
     * folders and several items are [PlayContext.None]. A screen playing the songs of one of those passes it itself.
     */
    val playContext: PlayContext
        get() = when (this) {
            is Albums -> albums.singleOrNull()?.playContext
            is AlbumArtists -> albumArtists.singleOrNull()?.playContext
            is Genres -> genres.singleOrNull()?.playContext
            is Playlists -> playlists.singleOrNull()?.playContext
            is Songs, is SongsMatching, is Folders, is Queue -> null
        } ?: PlayContext.None

    /** How many items the selection holds, for messages like "3 albums added to queue"; null for the queue. */
    val size: Int?
        get() = when (this) {
            is Songs -> songs.size
            is Albums -> albums.size
            is AlbumArtists -> albumArtists.size
            is Genres -> genres.size
            is Playlists -> playlists.size
            is Folders -> paths.size
            is SongsMatching, is Queue -> null
        }
}
