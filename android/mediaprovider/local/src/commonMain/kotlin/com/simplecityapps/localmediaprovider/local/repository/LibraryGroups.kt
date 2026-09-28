package com.simplecityapps.localmediaprovider.local.repository

import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.AlbumArtistGroupKey
import com.simplecityapps.shuttle.model.AlbumGroupKey
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
    artworkVersion = combinedArtworkVersion()
)

/** The album artist [key] names, made of their songs, as [toAlbum] makes an album. */
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
 * The album artist these songs' albums show ([com.simplecityapps.shuttle.model.AlbumIdentity.albumArtistName]): the
 * album artist tag, "Various Artists", or the artist every song of an untagged album agrees on.
 */
private fun List<Song>.albumArtistName(): String? = firstNotNullOfOrNull { it.resolvedAlbumIdentity.albumArtistName }
