package com.simplecityapps.shuttle.mediaprovider.jellyfin.http.data

import com.benasher44.uuid.uuid4
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.model.SongData
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate

fun ItemResponse.toSongData(): SongData {
    return SongData(
        name = name,
        albumArtist = albumArtist,
        artists = artists.filter { it.isNotEmpty() },
        album = album,
        track = indexNumber,
        disc = parentIndexNumber,
        duration = ((runTime ?: 0) / (10 * 1000)).toInt(),
        date = productionYear?.let { year -> LocalDate(year, 1, 1) },
        genres = genres,
        path = "jellyfin://item/${id}",
        size = 0,
        mimeType = "audio/*",
        dateModified = Clock.System.now(),
        lastPlayed = null,
        lastCompleted = null,
        externalId = id,
        mediaProvider = MediaProviderType.Jellyfin,
        lyrics = null,
        grouping = null,
        bitRate = null,
        sampleRate = null,
        channelCount = null,
        replayGainTrack = null,
        replayGainAlbum = null,
        composer = null
    )
}