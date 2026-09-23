package com.simplecityapps.playback.fakes

import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song

/** A local [Song] named "Song<id>", defaulting every field a test doesn't care about. */
fun testSong(
    id: Long,
    duration: Int = 180_000
) = Song(
    id = id,
    name = "Song$id",
    albumArtist = null,
    artists = emptyList(),
    album = null,
    track = null,
    disc = null,
    duration = duration,
    date = null,
    genres = emptyList(),
    path = "/music/song$id.mp3",
    size = 0,
    mimeType = "audio/mpeg",
    lastModified = null,
    lastPlayed = null,
    lastCompleted = null,
    playCount = 0,
    playbackPosition = 0,
    blacklisted = false,
    mediaProvider = MediaProviderType.Shuttle,
    lyrics = null,
    grouping = null,
    bitRate = null,
    bitDepth = null,
    sampleRate = null,
    channelCount = null
)
