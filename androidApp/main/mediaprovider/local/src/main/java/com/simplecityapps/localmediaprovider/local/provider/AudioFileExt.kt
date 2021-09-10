package com.simplecityapps.localmediaprovider.local.provider

import com.simplecityapps.ktaglib.KTagLib
import com.simplecityapps.mediaprovider.model.AudioFile
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import java.util.*

fun AudioFile.toSong(providerType: MediaProviderType): Song {
    return Song(
        id = 0,
        name = title,
        artists = artists,
        albumArtist = albumArtist,
        album = album,
        track = track,
        disc = disc,
        duration = duration ?: 0,
        date = year?.toIntOrNull()?.let { LocalDate(it, 1, 1) },
        genres = genres,
        path = path,
        size = size,
        mimeType = mimeType,
        lastModified = Instant.fromEpochMilliseconds(lastModified),
        lastPlayed = null,
        lastCompleted = null,
        playCount = 0,
        playbackPosition = 0,
        blacklisted = false,
        mediaProvider = providerType,
        replayGainTrack = replayGainTrack,
        replayGainAlbum = replayGainAlbum,
        lyrics = lyrics,
        grouping = grouping,
        bitRate = bitRate,
        bitDepth = bitDepth,
        sampleRate = sampleRate,
        channelCount = channelCount
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
    Year("YEAR"),
    ReplayGainTrack("REPLAYGAIN_TRACK_GAIN"),
    ReplayGainAlbum("REPLAYGAIN_ALBUM_GAIN"),
    Lyrics("LYRICS"),
    Grouping("GROUPING")
}

fun KTagLib.getAudioFile(fileDescriptor: Int, filePath: String, fileName: String, lastModified: Long, size: Long, mimeType: String?): AudioFile {
    val metadata = getMetadata(fileDescriptor)
    return AudioFile(
        path = filePath,
        size = size,
        lastModified = lastModified,
        mimeType = mimeType ?: "audio/*",
        title = metadata?.propertyMap?.get(TagLibProperty.Title.key)?.firstOrNull() ?: fileName,
        albumArtist = metadata?.propertyMap?.get(TagLibProperty.AlbumArtist.key)?.firstOrNull(),
        artists = metadata?.propertyMap?.get(TagLibProperty.Artist.key).orEmpty().flatMap { artist ->
            artist.split(';', '\u0000')
                .map { artist -> artist.trim() }
                .filterNot { artist -> artist.isEmpty() }
        },
        album = metadata?.propertyMap?.get(TagLibProperty.Album.key)?.firstOrNull(),
        track = metadata?.propertyMap?.get(TagLibProperty.Track.key)?.firstOrNull()?.substringBefore('/')?.toIntOrNull(),
        trackTotal = metadata?.propertyMap?.get(TagLibProperty.Track.key)?.firstOrNull()?.substringAfter('/', "")?.toIntOrNull(),
        disc = metadata?.propertyMap?.get(TagLibProperty.Disc.key)?.firstOrNull()?.substringBefore('/')?.toIntOrNull(),
        discTotal = metadata?.propertyMap?.get(TagLibProperty.Disc.key)?.firstOrNull()?.substringAfter('/', "")?.toIntOrNull(),
        duration = metadata?.audioProperties?.duration,
        year = (metadata?.propertyMap?.get(TagLibProperty.Date.key)?.firstOrNull()
            ?: metadata?.propertyMap?.get(TagLibProperty.OriginalDate.key)?.firstOrNull())?.parseDate()
            ?: metadata?.propertyMap?.get(TagLibProperty.Year.key)?.firstOrNull()?.parseDate(),
        genres = metadata?.propertyMap?.get(TagLibProperty.Genre.key).orEmpty().flatMap { genre ->
            genre.split(',', ';', '/', '\u0000')
                .map { genre -> genre.trim() }
                .filterNot { genre -> genre.isEmpty() }
        },
        replayGainTrack = metadata?.propertyMap?.getCaseInsensitive(TagLibProperty.ReplayGainTrack.key)
            ?.firstOrNull()?.replace(oldValue = "db", newValue = "", ignoreCase = true)
            ?.toDoubleOrNull(),
        replayGainAlbum = metadata?.propertyMap?.getCaseInsensitive(TagLibProperty.ReplayGainAlbum.key)
            ?.firstOrNull()?.replace(oldValue = "db", newValue = "", ignoreCase = true)
            ?.toDoubleOrNull(),
        lyrics = metadata?.propertyMap?.get(TagLibProperty.Lyrics.key)?.firstOrNull(),
        grouping = metadata?.propertyMap?.get(TagLibProperty.Grouping.key)?.firstOrNull(),
        bitRate = metadata?.audioProperties?.bitrate,
        bitDepth = null,
        sampleRate = metadata?.audioProperties?.sampleRate,
        channelCount = metadata?.audioProperties?.channelCount
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

fun <V> Map<String, V>.getCaseInsensitive(key: String): V? {
    return get(key) ?: get(key.lowercase(Locale.US))
}