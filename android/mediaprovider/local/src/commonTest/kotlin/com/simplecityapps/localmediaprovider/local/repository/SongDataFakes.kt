package com.simplecityapps.localmediaprovider.local.repository

import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import com.simplecityapps.localmediaprovider.local.data.room.entity.SongData
import com.simplecityapps.shuttle.model.AlbumIndex
import com.simplecityapps.shuttle.model.AlbumIndexProvider
import kotlin.time.Instant

internal fun createSongData(
    album: String,
    albumArtist: String = "Artist",
    track: Int = 1,
    playCount: Int = 0
) = SongData(
    name = "Song",
    track = track,
    disc = 1,
    duration = 180_000,
    year = null,
    genres = emptyList(),
    path = "/music/$albumArtist/$album/$track.mp3",
    albumArtist = albumArtist,
    artists = listOf("Artist"),
    album = album,
    size = 0,
    mimeType = "audio/mpeg",
    lastModified = Instant.fromEpochMilliseconds(0),
    lyrics = null,
    grouping = null,
    bitRate = null,
    bitDepth = null,
    sampleRate = null,
    channelCount = null,
    playCount = playCount
)

/** An index built fresh from [database] each time it's asked for, for tests that don't exercise its caching. */
internal fun freshAlbumIndex(database: MediaDatabase) = AlbumIndexProvider { AlbumIndex(database.songDataDao().identityData().map { it.toTags() }) }
