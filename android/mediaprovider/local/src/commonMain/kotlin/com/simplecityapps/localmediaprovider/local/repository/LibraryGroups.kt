package com.simplecityapps.localmediaprovider.local.repository

import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.AlbumArtistGroupKey
import com.simplecityapps.shuttle.model.AlbumGroupKey
import com.simplecityapps.shuttle.model.AlbumIdentityRule
import com.simplecityapps.shuttle.model.ArtistCredits
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.model.isAlbumArtist

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
    dateAdded = mapNotNull { it.dateAdded }.maxOrNull(),
    albumArtistKeys = firstOrNull()?.resolvedAlbumIdentity?.albumArtistKeys ?: listOfNotNull(key.albumArtistGroupKey)
)

/** The album artist [key] names, made of their own albums' songs (any others are left out), as [toAlbum] makes an album (Home's suggestions). */
internal fun List<Song>.toAlbumArtist(key: AlbumArtistGroupKey): AlbumArtist {
    val songs = filter { song -> song.isAlbumArtist(key) }
    return AlbumArtist(
        name = songs.albumArtistName(key),
        artists = songs.flatMap { it.artists }.distinct(),
        albumCount = songs.distinctBy { it.albumGroupKey }.size,
        songCount = songs.size,
        // Every play of any of their songs counts, so never played means none of them ever was
        playCount = songs.sumOf { it.playCount },
        groupKey = key,
        mediaProviders = songs.map { it.mediaProvider }.distinct(),
        artworkVersion = songs.combinedArtworkVersion()
    )
}

/**
 * The library's artists (#637), made of these songs (the whole library's): every album artist ([AlbumIdentityRule]),
 * with their own albums' songs, and every artist a song credits ([ArtistCredits]) with the songs crediting them. One
 * artist per key, so an album artist also credited elsewhere is one artist, and one only credited has no albums. An
 * album with several album artists ([com.simplecityapps.shuttle.model.AlbumIdentity.albumArtists]) is each one's own.
 */
internal fun List<Song>.toArtists(): List<AlbumArtist> {
    val own = HashMap<AlbumArtistGroupKey, MutableList<Song>>()
    // The songs crediting each artist on someone else's album, with the name each credits them by
    val credited = HashMap<AlbumArtistGroupKey, MutableList<Pair<Song, String>>>()
    forEach { song ->
        val owners = song.resolvedAlbumIdentity.albumArtistKeys
        owners.forEach { key -> own.getOrPut(key) { mutableListOf() } += song }
        song.artistCredits
            .filter { credit -> credit.groupKey !in owners }
            .forEach { credit -> credited.getOrPut(credit.groupKey) { mutableListOf() } += song to credit.name }
    }
    return (own.keys + credited.keys).map { key ->
        val ownSongs = own[key].orEmpty()
        val appearances = credited[key].orEmpty()
        val songs = ownSongs + appearances.map { (song, _) -> song }
        AlbumArtist(
            name = ownSongs.albumArtistName(key) ?: appearances.groupingBy { (_, name) -> name }.eachCount().maxByOrNull { it.value }?.key,
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

/**
 * The name these songs' albums give their album artist [key]: the album artist shown, unless the album names them
 * among others (one of several album artists, or "A" of "A feat. B"), when it's their own name.
 */
private fun List<Song>.albumArtistName(key: AlbumArtistGroupKey): String? = firstNotNullOfOrNull { song ->
    val identity = song.resolvedAlbumIdentity
    if (key == identity.albumArtistGroupKey) identity.albumArtistName else identity.albumArtists.firstOrNull { it.groupKey == key }?.name
}
