package com.simplecityapps.localmediaprovider.local.provider

import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.taglib.AudioFile
import java.util.*

fun AudioFile.toSong(mimeType: String): Song {
    return Song(
        id = 0,
        name = name,
        albumArtistId = 0,
        albumArtistName = albumArtistName,
        albumId = 0,
        albumName = albumName,
        track = track,
        disc = disc,
        duration = duration,
        year = year,
        path = path,
        size = size,
        mimeType = mimeType,
        lastModified = Date(lastModified),
        lastPlayed = null,
        lastCompleted = null,
        playCount = 0,
        playbackPosition = 0,
        blacklisted = false
    )
}