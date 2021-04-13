package com.simplecityapps.localmediaprovider.local.provider

import com.simplecityapps.ktaglib.KTagLib
import com.simplecityapps.mediaprovider.MediaProvider
import com.simplecityapps.mediaprovider.model.AudioFile
import com.simplecityapps.mediaprovider.model.Song
import java.util.*

fun AudioFile.toSong(providerType: MediaProvider.Type): Song {
    return Song(
        id = 0,
        name = title,
        artists = artists,
        albumArtist = albumArtist,
        album = album,
        track = track,
        disc = disc,
        duration = duration ?: 0,
        year = year?.toIntOrNull(),
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
        mediaProvider = providerType,
        replayGainTrack = replayGainTrack,
        replayGainAlbum = replayGainAlbum
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
    OriginalDate("ORIGINALDATE"),
    ReplayGainTrack("REPLAYGAIN_TRACK_GAIN"),
    ReplayGainAlbum("REPLAYGAIN_ALBUM_GAIN"),
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
        artists = metadata?.propertyMap?.get(TagLibProperty.Artist.key).orEmpty().flatMap { artist ->
            artist.split(",", ";", "/")
                .map { artist -> artist.trim() }
                .filterNot { artist -> artist.isEmpty() }
        },
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
        },
        replayGainTrack = metadata?.propertyMap?.get(TagLibProperty.ReplayGainTrack.key)?.firstOrNull()?.take(9)?.toDoubleOrNull(),
        replayGainAlbum = metadata?.propertyMap?.get(TagLibProperty.ReplayGainAlbum.key)?.firstOrNull()?.take(9)?.toDoubleOrNull(),
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