package com.simplecityapps.localmediaprovider.local.provider

import com.simplecityapps.ktaglib.KTagLib
import com.simplecityapps.mediaprovider.MediaProvider
import com.simplecityapps.mediaprovider.model.AudioFile
import com.simplecityapps.mediaprovider.model.Song
import java.util.*

fun AudioFile.toSong(providerType: MediaProvider.Type): Song {
    return Song(
        id = 0,
        name = title ?: "Unknown",
        artist = artist ?: "Unknown",
        albumArtist = albumArtist ?: artist ?: "Unknown",
        album = album ?: "Unknown",
        track = track ?: 1,
        disc = disc ?: 1,
        duration = duration ?: 0,
        year = year?.toIntOrNull() ?: 0,
        genres = genres,
        path = path,
        size = size,
        mimeType = mimeType,
        lastModified = Date(lastModified),
        lastPlayed = null,
        lastCompleted = null,
        playCount = 0,
        playbackPosition = 0,
        blacklisted = false,
        mediaProvider = providerType
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
    Genre("GENRE"),
    OriginalDate("ORIGINALDATE")
}

fun getAudioFile(fileDescriptor: Int, filePath: String, fileName: String, lastModified: Long, size: Long, mimeType: String): AudioFile {
    val metadata = KTagLib.getMetadata(fileDescriptor)

    return AudioFile(
        filePath,
        size,
        lastModified,
        mimeType,
        title = metadata?.propertyMap?.get(TagLibProperty.Title.key)?.firstOrNull() ?: fileName,
        albumArtist = metadata?.propertyMap?.get(TagLibProperty.AlbumArtist.key)?.firstOrNull(),
        artist = metadata?.propertyMap?.get(TagLibProperty.Artist.key)?.firstOrNull(),
        album = metadata?.propertyMap?.get(TagLibProperty.Album.key)?.firstOrNull(),
        track = metadata?.propertyMap?.get(TagLibProperty.Track.key)?.firstOrNull()?.substringBefore('/')?.toIntOrNull(),
        trackTotal = metadata?.propertyMap?.get(TagLibProperty.Track.key)?.firstOrNull()?.substringAfter('/', "")?.toIntOrNull(),
        disc = metadata?.propertyMap?.get(TagLibProperty.Disc.key)?.firstOrNull()?.substringBefore('/')?.toIntOrNull(),
        discTotal = metadata?.propertyMap?.get(TagLibProperty.Disc.key)?.firstOrNull()?.substringAfter('/', "")?.toIntOrNull(),
        duration = metadata?.audioProperties?.duration,
        year = (metadata?.propertyMap?.get(TagLibProperty.Date.key)?.firstOrNull() ?: metadata?.propertyMap?.get(TagLibProperty.OriginalDate.key)?.firstOrNull())?.parseDate(),
        genres = metadata?.propertyMap?.get(TagLibProperty.Genre.key).orEmpty().flatMap { genre ->
            genre.split(",", ";", "/")
                .map { genre -> genre.trim() }
                .filterNot { genre -> genre.isEmpty() }
        }
    )
}


fun String.parseDate(): String? {
    if (length < 4) {
        return null
    } else if (length > 4) {
        return substring(0, 4)
    }
    return this
}