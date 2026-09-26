package com.simplecityapps.shuttle.scrobbling

import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song

fun createSong(
    id: Long,
    duration: Int,
    mediaProvider: MediaProviderType = MediaProviderType.Shuttle
) = Song(
    id = id,
    name = "song-$id",
    albumArtist = "album-artist",
    artists = listOf("artist"),
    album = "album",
    track = 1,
    disc = 1,
    duration = duration,
    date = null,
    genres = emptyList(),
    path = "/music/song-$id.mp3",
    size = 1,
    mimeType = "audio/mpeg",
    lastModified = null,
    lastPlayed = null,
    lastCompleted = null,
    playCount = 0,
    playbackPosition = 0,
    blacklisted = false,
    mediaProvider = mediaProvider,
    lyrics = null,
    grouping = null,
    bitRate = null,
    bitDepth = null,
    sampleRate = null,
    channelCount = null
)
