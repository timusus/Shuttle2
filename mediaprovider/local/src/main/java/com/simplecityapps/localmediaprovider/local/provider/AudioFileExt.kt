package com.simplecityapps.localmediaprovider.local.provider

import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.taglib.AudioFile
import java.util.*

fun AudioFile.toSong(mimeType: String): Song {
    return Song(0, name, 0, albumArtistName, 0, albumName, track, disc, duration, year, path, size, mimeType, Date(lastModified), null, null, 0, 0)
}