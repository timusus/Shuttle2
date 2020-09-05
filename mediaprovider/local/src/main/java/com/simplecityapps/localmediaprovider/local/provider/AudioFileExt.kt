package com.simplecityapps.localmediaprovider.local.provider

import com.simplecityapps.ktaglib.KTagLib
import com.simplecityapps.mediaprovider.model.AudioFile
import com.simplecityapps.mediaprovider.model.Song
import java.util.*

fun AudioFile.toSong(mimeType: String): Song {
    return Song(
        id = 0,
        name = title ?: "Unknown",
        artist = artist ?: "Unknown",
        albumArtist = albumArtist ?: artist ?: "Unknown",
        album = album ?: "Unknown",
        track = track ?: 1,
        disc = disc ?: 1,
        duration = duration ?: 0,
        year = date?.toIntOrNull() ?: 0,
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

enum class TagLibProperty(val key: String) {
    Title("TITLE"),
    Artist("ARTIST"),
    Album("ALBUM"),
    AlbumArtist("ALBUMARTIST"),
    Date("DATE"),
    Track("TRACKNUMBER"),
    Disc("DISCNUMBER"),
    Genre("GENRE")
}

fun getAudioFile(fileDescriptor: Int, filePath: String, fileName: String, lastModified: Long, size: Long): AudioFile {
    val metadata = KTagLib.getMetadata(fileDescriptor)
    return AudioFile(
        filePath,
        size,
        lastModified,
        title = metadata?.propertyMap?.get(TagLibProperty.Title.key)?.firstOrNull() ?: fileName,
        albumArtist = metadata?.propertyMap?.get(TagLibProperty.AlbumArtist.key)?.firstOrNull(),
        artist = metadata?.propertyMap?.get(TagLibProperty.Artist.key)?.firstOrNull(),
        album = metadata?.propertyMap?.get(TagLibProperty.Album.key)?.firstOrNull(),
        track = metadata?.propertyMap?.get(TagLibProperty.Track.key)?.firstOrNull()?.substringBefore('/')?.toIntOrNull(),
        trackTotal = metadata?.propertyMap?.get(TagLibProperty.Track.key)?.firstOrNull()?.substringAfter('/', "")?.toIntOrNull(),
        disc = metadata?.propertyMap?.get(TagLibProperty.Disc.key)?.firstOrNull()?.substringBefore('/')?.toIntOrNull(),
        discTotal = metadata?.propertyMap?.get(TagLibProperty.Disc.key)?.firstOrNull()?.substringAfter('/', "")?.toIntOrNull(),
        duration = metadata?.audioProperties?.duration,
        date = metadata?.propertyMap?.get(TagLibProperty.Date.key)?.firstOrNull(),
        genre = metadata?.propertyMap?.get(TagLibProperty.Genre.key)?.firstOrNull()
    )
}