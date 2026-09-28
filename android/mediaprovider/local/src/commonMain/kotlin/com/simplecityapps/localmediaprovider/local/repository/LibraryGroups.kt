package com.simplecityapps.localmediaprovider.local.repository

import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.AlbumArtistGroupKey
import com.simplecityapps.shuttle.model.AlbumGroupKey
import com.simplecityapps.shuttle.model.Song

/** The album [key] names, made of its songs: the album and suggestions repositories both build albums with this. */
internal fun List<Song>.toAlbum(key: AlbumGroupKey): Album = Album(
    name = firstOrNull { it.album != null }?.album,
    albumArtist = firstOrNull { it.albumArtist != null }?.albumArtist,
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
    name = firstOrNull { it.albumArtist != null }?.albumArtist,
    artists = flatMap { it.artists }.distinct(),
    albumCount = distinctBy { it.album }.size,
    songCount = size,
    // Every play of any of their songs counts, so never played means none of them ever was
    playCount = sumOf { it.playCount },
    groupKey = key,
    mediaProviders = map { it.mediaProvider }.distinct(),
    artworkVersion = combinedArtworkVersion()
)
