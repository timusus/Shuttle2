package com.simplecityapps.shuttle.playbackreporting

import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import kotlin.time.Instant

/**
 * A [Song] carrying only what playback reporting reads: its id, name, duration and provider. Local to this
 * source set because :android:presentation-testing's `createSong` depends on :android:presentation, which
 * depends on this module.
 */
internal fun createSong(
    id: Long,
    name: String = "song-name",
    duration: Int = 1,
    mediaProvider: MediaProviderType = MediaProviderType.Shuttle
) = Song(
    id = id,
    name = name,
    albumArtist = "album-artist",
    artists = emptyList(),
    album = "album-name",
    track = 1,
    disc = 1,
    duration = duration,
    date = null,
    genres = emptyList(),
    path = "/path/to/song",
    size = 1,
    mimeType = "ogg",
    lastModified = Instant.fromEpochSeconds(1),
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
