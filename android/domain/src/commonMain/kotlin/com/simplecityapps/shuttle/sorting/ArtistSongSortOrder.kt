package com.simplecityapps.shuttle.sorting

import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.Song

/**
 * The sorts an artist's detail screen offers, a subset of [SongListSort]. The album orders group songs under
 * their albums, each in disc/track order; [SongTitle] and [MostPlayed] list them flat.
 */
enum class ArtistSongSortOrder(val sort: SongListSort) {
    AlbumNewest(SongListSort.AlbumNewest),
    AlbumOldest(SongListSort.AlbumOldest),
    AlbumTitle(SongListSort.AlbumTitle),
    SongTitle(SongListSort.SongTitle),
    MostPlayed(SongListSort.MostPlayed),
    ;

    /** Whether songs are grouped under their albums, which [albumComparator] then orders. */
    val groupsByAlbum: Boolean get() = sort.groupsByAlbum

    /** The order of the album groups, or null for the flat orders. */
    val albumComparator: Comparator<Album>? get() = sort.albumComparator

    /** The order of songs within an album group, or of the whole list for the flat orders. */
    val songComparator: Comparator<Song> get() = checkNotNull(sort.songComparator)

    companion object {
        val Default = AlbumNewest
    }
}

object ArtistSongComparator {
    private val collator: Comparator<String> by lazy { localeCollator(CollationStrength.Tertiary) }

    private val albumName: Comparator<Album> by lazy { compareBy(nullsLast(collator)) { album: Album -> album.name } }

    /** Newest first; albums without a year last, then by title. */
    val albumNewest: Comparator<Album> by lazy {
        compareBy<Album, Int?>(nullsLast(reverseOrder())) { it.year }.then(albumName)
    }

    /** Oldest first; albums without a year last, then by title. */
    val albumOldest: Comparator<Album> by lazy {
        compareBy<Album, Int?>(nullsLast()) { it.year }.then(albumName)
    }

    /** A–Z by title, then oldest first. */
    val albumTitle: Comparator<Album> by lazy {
        albumName.then(compareBy(nullsLast()) { album: Album -> album.year })
    }

    private val songName: Comparator<Song> by lazy { compareBy(nullsLast(collator)) { song: Song -> song.name } }

    /** Disc, then track, then title; a missing disc or track sorts first, as in the album's own track list. */
    val trackOrder: Comparator<Song> by lazy {
        compareBy<Song>({ it.disc }, { it.track })
            .then(songName)
    }

    /** A–Z by title, then by album and track order. */
    val songTitle: Comparator<Song> by lazy {
        songName
            .then(compareBy(nullsLast(collator)) { song: Song -> song.album })
            .then(trackOrder)
    }

    /** Most plays first, then by title. */
    val mostPlayed: Comparator<Song> by lazy {
        compareByDescending<Song> { it.playCount }.then(songTitle)
    }
}
