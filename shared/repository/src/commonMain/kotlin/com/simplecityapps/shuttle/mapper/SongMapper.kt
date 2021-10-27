package com.simplecityapps.shuttle.mapper

import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import comsimplecityappsshuttle.common.database.SongEntity
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

fun SongEntity.toSong(): Song {
    return Song(
        id = id,
        name = name,
        albumArtist = albumArtist,
        artists = artists?.split(";") ?: emptyList(),
        album = album,
        track = trackNumber,
        disc = discNumber,
        duration = duration,
        date = date?.let { LocalDate.parse(it) },
        genres = genres?.split(";") ?: emptyList(),
        path = path,
        size = size,
        mimeType = mimeType,
        dateModified = dateModified?.let { Instant.parse(it) },
        lastPlayed = lastPlayed?.let { Instant.parse(it) },
        lastCompleted = lastCompleted?.let { Instant.parse(it) },
        playCount = playCount ?: 0,
        playbackPosition = playbackPosition ?: 0,
        blacklisted = excluded,
        externalId = externalId,
        mediaProvider = MediaProviderType.valueOf(mediaProvider),
        replayGainTrack = replayGainTrack,
        replayGainAlbum = replayGainAlbum,
        lyrics = lyrics,
        grouping = grouping,
        bitRate = bitRate,
        sampleRate = sampleRate,
        channelCount = channelCount,
        composer = composer,
        dateAdded = Instant.parse(dateAdded)
    )
}

fun Song.toSongEntity(): SongEntity {
    return SongEntity(
        id = id,
        path = path,
        name = name,
        artists = artists.joinToString(";"),
        album = album,
        albumArtist = albumArtist,
        trackNumber = track,
        discNumber = disc,
        duration = duration,
        date = date?.toString(),
        genres = genres.joinToString(";"),
        size = size,
        mimeType = mimeType,
        playbackPosition = playbackPosition,
        playCount = playCount,
        lastPlayed = lastPlayed?.toString(),
        lastCompleted = lastCompleted?.toString(),
        excluded = blacklisted,
        mediaProvider = mediaProvider.name,
        externalId = externalId,
        replayGainTrack = replayGainTrack,
        replayGainAlbum = replayGainAlbum,
        lyrics = lyrics,
        grouping = grouping,
        composer = composer,
        bitRate = bitRate,
        sampleRate = sampleRate,
        channelCount = channelCount,
        dateModified = dateModified?.toString(),
        dateAdded = dateAdded.toString()
    )
}