package com.simplecityapps.localmediaprovider.local.repository

import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.AlbumArtistGroupKey
import com.simplecityapps.shuttle.model.AlbumGroupKey
import com.simplecityapps.shuttle.model.AlbumIdentityRule
import com.simplecityapps.shuttle.model.ArtistCredits
import com.simplecityapps.shuttle.model.Song

/** The album [key] names, made of its songs: the album and suggestions repositories both build albums with this. */
internal fun List<Song>.toAlbum(key: AlbumGroupKey): Album = Album(
    name = firstOrNull { it.album != null }?.album,
    albumArtist = albumArtistName(),
    artists = flatMap { it.artists }.distinct(),
    songCount = size,
    duration = sumOf { it.duration },
    year = mapNotNull { it.date?.year }.minOrNull(),
    // Every play of any of its songs counts, so one skipped track doesn't keep an album out of Most Played
    playCount = sumOf { it.playCount },
    lastSongPlayed = mapNotNull { it.lastPlayed }.maxOrNull(),
    lastSongCompleted = mapNotNull { it.lastCompleted }.maxOrNull(),
    groupKey = key,
    mediaProviders = map { it.mediaProvider }.distinct(),
    artworkVersion = combinedArtworkVersion(),
    dateAdded = mapNotNull { it.dateAdded }.maxOrNull()
)

/** The album artist [key] names, made of their own albums' songs, as [toAlbum] makes an album (Home's suggestions). */
internal fun List<Song>.toAlbumArtist(key: AlbumArtistGroupKey): AlbumArtist = AlbumArtist(
    name = albumArtistName(),
    artists = flatMap { it.artists }.distinct(),
    albumCount = distinctBy { it.albumGroupKey }.size,
    songCount = size,
    // Every play of any of their songs counts, so never played means none of them ever was
    playCount = sumOf { it.playCount },
    groupKey = key,
    mediaProviders = map { it.mediaProvider }.distinct(),
    artworkVersion = combinedArtworkVersion()
)

/**
 * The library's artists (#637), made of these songs (the whole library's): every album artist ([AlbumIdentityRule]),
 * with their own albums' songs, and every artist a song credits ([ArtistCredits]) with the songs crediting them. One
 * artist per key, so an album artist also credited elsewhere is one artist, and one only credited has no albums.
 */
internal fun List<Song>.toArtists(): List<AlbumArtist> {
    val own = groupBy { song -> song.albumArtistGroupKey }
    // The songs crediting each artist on someone else's album, with the name each credits them by
    val credited = HashMap<AlbumArtistGroupKey, MutableList<Pair<Song, String>>>()
    forEach { song ->
        song.artistCredits
            .filter { credit -> credit.groupKey != song.albumArtistGroupKey }
            .forEach { credit -> credited.getOrPut(credit.groupKey) { mutableListOf() } += song to credit.name }
    }
    return (own.keys + credited.keys).map { key ->
        val ownSongs = own[key].orEmpty()
        val appearances = credited[key].orEmpty()
        val songs = ownSongs + appearances.map { (song, _) -> song }
        AlbumArtist(
            name = ownSongs.albumArtistName() ?: appearances.groupingBy { (_, name) -> name }.eachCount().maxByOrNull { it.value }?.key,
            artists = ownSongs.flatMap { it.artists }.distinct().ifEmpty { appearances.map { (_, name) -> name }.distinct() },
            albumCount = ownSongs.distinctBy { it.albumGroupKey }.size,
            songCount = songs.size,
            // Every play of any of their songs counts, so never played means none of them ever was
            playCount = songs.sumOf { it.playCount },
            groupKey = key,
            mediaProviders = songs.map { it.mediaProvider }.distinct(),
            artworkVersion = songs.combinedArtworkVersion(),
            appearsOnCount = appearances.filter { (song, _) -> !song.album.isNullOrBlank() }.distinctBy { (song, _) -> song.albumGroupKey }.size
        )
    }
}

/**
 * The album artist these songs' albums show ([com.simplecityapps.shuttle.model.AlbumIdentity.albumArtistName]): the
 * album artist tag, "Various Artists", or the artist every song of an untagged album agrees on.
 */
private fun List<Song>.albumArtistName(): String? = firstNotNullOfOrNull { it.resolvedAlbumIdentity.albumArtistName }
