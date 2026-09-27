package com.simplecityapps.shuttle.shared.playback

import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song

/** A song with [id], [duration] long; a [path] with "podcast" in it makes it a podcast. */
fun song(
    id: Long,
    duration: Int = 200_000,
    path: String = "/music/$id.flac",
    playbackPosition: Int = 0
) = Song(
    id = id,
    name = "Song $id",
    albumArtist = "Artist",
    artists = listOf("Artist"),
    album = "Album",
    track = id.toInt(),
    disc = 1,
    duration = duration,
    date = null,
    genres = emptyList(),
    path = path,
    size = 0,
    mimeType = "audio/flac",
    lastModified = null,
    lastPlayed = null,
    lastCompleted = null,
    playCount = 0,
    playbackPosition = playbackPosition,
    blacklisted = false,
    mediaProvider = MediaProviderType.Shuttle,
    lyrics = null,
    grouping = null,
    bitRate = null,
    bitDepth = null,
    sampleRate = null,
    channelCount = null
)
