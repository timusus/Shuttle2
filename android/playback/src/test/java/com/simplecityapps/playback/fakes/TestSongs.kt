package com.simplecityapps.playback.fakes

import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song

/** A local [Song] named "Song<id>" by default, defaulting every field a test doesn't care about. */
fun testSong(
    id: Long,
    name: String = "Song$id",
    path: String = "/music/song$id.mp3",
    mimeType: String = "audio/mpeg",
    duration: Int = 180_000,
    replayGainTrack: Double? = null,
    replayGainAlbum: Double? = null,
    album: String? = null
) = Song(
    id = id,
    name = name,
    albumArtist = null,
    artists = emptyList(),
    album = album,
    track = null,
    disc = null,
    duration = duration,
    date = null,
    genres = emptyList(),
    path = path,
    size = 0,
    mimeType = mimeType,
    lastModified = null,
    lastPlayed = null,
    lastCompleted = null,
    playCount = 0,
    playbackPosition = 0,
    blacklisted = false,
    mediaProvider = MediaProviderType.Shuttle,
    replayGainTrack = replayGainTrack,
    replayGainAlbum = replayGainAlbum,
    lyrics = null,
    grouping = null,
    bitRate = null,
    bitDepth = null,
    sampleRate = null,
    channelCount = null
)
