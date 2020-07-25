package com.simplecityapps.localmediaprovider.local.provider

import com.simplecityapps.ktaglib.AudioFile
import com.simplecityapps.mediaprovider.model.Song
import java.util.*

fun AudioFile.toSong(mimeType: String): Song {
    return Song(
        id = 0,
        name = title ?: "Unknown",
        albumArtist = albumArtist ?: "Unknown",
        album = album ?: "Unknown",
        track = track ?: 1,
        disc = disc ?: 1,
        duration = duration ?: 0,
        year = year ?: 0,
        path = path,
        size = size,
        mimeType = mimeType,
        lastModified = Date(lastModified),
        lastPlayed = null,
        lastCompleted = null,
        playCount = 0,
        playbackPosition = 0,
        excluded = false
    )
}