package com.simplecityapps.shuttle.sorting

import com.simplecityapps.mediaprovider.repository.songs.SongComparator
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.Song
import kotlin.time.Instant

/**
 * How a detail screen lists songs: the album sorts group them under album sections, each in disc/track order;
 * the rest list them flat. Each screen offers a subset ([ArtistSongSortOrder], [PlaylistKind.sorts]), and every
 * platform lays them out with [songSections], so they render the same thing.
 */
enum class SongListSort {
    /** Albums by their most recently added song, newest first. */
    AlbumNewestAdded,

    /** Albums by year, newest first. */
    AlbumNewest,
    AlbumOldest,
    AlbumTitle,

    /** The order the songs arrive in: a playlist's stored order or its query's own sort. */
    PlaylistOrder,
    NewestAdded,
    SongTitle,
    Artist,
    MostPlayed,
    ;

    val groupsByAlbum: Boolean get() = albumComparator != null

    /** The order of the album sections, or null for the flat sorts. */
    val albumComparator: Comparator<Album>?
        get() = when (this) {
            AlbumNewestAdded -> SongListComparator.albumNewestAdded
            AlbumNewest -> ArtistSongComparator.albumNewest
            AlbumOldest -> ArtistSongComparator.albumOldest
            AlbumTitle -> ArtistSongComparator.albumTitle
            PlaylistOrder, NewestAdded, SongTitle, Artist, MostPlayed -> null
        }

    /** The order of songs within an album section, or of the whole flat list; null keeps the order they arrive in. */
    val songComparator: Comparator<Song>?
        get() = when (this) {
            AlbumNewestAdded, AlbumNewest, AlbumOldest, AlbumTitle -> ArtistSongComparator.trackOrder
            PlaylistOrder -> null
            NewestAdded -> SongComparator.dateAddedComparator
            SongTitle -> ArtistSongComparator.songTitle
            Artist -> SongComparator.artistGroupKeyComparator
            MostPlayed -> ArtistSongComparator.mostPlayed
        }
}

/** A run of a song list: [album]'s songs in track order, or, with no album, songs listed flat or off every album. */
data class SongSection(val album: Album?, val songs: List<Song>)

/**
 * [songs] laid out in [sort]: one section per album in [albums] that holds any of them, in the sort's album
 * order, then the songs on none of [albums] by title in a trailing section with no album; or, for a flat sort,
 * a single section with no album. [albums] defaults to the albums the songs themselves make up.
 * The sections flatten to the play order.
 */
fun songSections(songs: List<Song>, sort: SongListSort, albums: List<Album> = albumsOf(songs)): List<SongSection> {
    if (songs.isEmpty()) return emptyList()
    val albumComparator = sort.albumComparator ?: return listOf(SongSection(album = null, songs = songs.sortedBy(sort)))
    val songsByAlbum = songs.groupBy { it.albumGroupKey }
    val albumSections = albums.sortedWith(albumComparator).mapNotNull { album ->
        songsByAlbum[album.groupKey]?.let { SongSection(album, it.sortedBy(sort)) }
    }
    val albumKeys = albums.mapNotNullTo(HashSet()) { it.groupKey }
    val otherSongs = songs.filter { it.albumGroupKey !in albumKeys }.sortedWith(ArtistSongComparator.songTitle)
    return if (otherSongs.isEmpty()) albumSections else albumSections + SongSection(album = null, songs = otherSongs)
}

private fun List<Song>.sortedBy(sort: SongListSort): List<Song> = sort.songComparator?.let(::sortedWith) ?: this

/**
 * One album per album key among [songs], built from those songs alone: a playlist holds songs, not albums, and
 * its sections only need what the songs say (name, year, newest added, artwork).
 */
fun albumsOf(songs: List<Song>): List<Album> = songs.groupBy { it.albumGroupKey }.map { (key, albumSongs) ->
    val first = albumSongs.first()
    Album(
        name = first.album,
        albumArtist = first.albumArtist,
        artists = albumSongs.flatMap { it.artists }.distinct(),
        songCount = albumSongs.size,
        duration = albumSongs.sumOf { it.duration },
        year = albumSongs.firstNotNullOfOrNull { it.date?.year },
        playCount = albumSongs.sumOf { it.playCount },
        lastSongPlayed = albumSongs.mapNotNull { it.lastPlayed }.maxOrNull(),
        lastSongCompleted = albumSongs.mapNotNull { it.lastCompleted }.maxOrNull(),
        groupKey = key,
        mediaProviders = albumSongs.map { it.mediaProvider }.distinct(),
        artworkVersion = first.artworkVersion,
        dateAdded = albumSongs.mapNotNull { it.dateAdded }.maxOrNull(),
        albumArtistKeys = first.albumArtistKeys,
    )
}

object SongListComparator {
    /** Most recently added first; albums without a date last, then by title. */
    val albumNewestAdded: Comparator<Album> by lazy {
        compareBy<Album, Instant?>(nullsLast(reverseOrder())) { it.dateAdded }.then(ArtistSongComparator.albumTitle)
    }
}
