package com.simplecityapps.localmediaprovider.local.provider

import com.simplecityapps.ktaglib.KTagLib
import com.simplecityapps.mediaprovider.model.AudioFile
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.Locale
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

fun AudioFile.toSong(
    providerType: MediaProviderType,
    folderImages: Collection<FolderImage>
): Song = Song(
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
    channelCount = channelCount,
    artworkVersion = localArtworkVersion(lastModified, folderImages)
)

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

fun KTagLib.getAudioFile(
    fileDescriptor: Int,
    filePath: String,
    fileName: String,
    lastModified: Long,
    size: Long,
    mimeType: String?
): AudioFile {
    val metadata = getMetadata(fileDescriptor, fileName)
    val tags = metadata?.propertyMap.orEmpty().toFileTags()
    return AudioFile(
        path = filePath,
        size = size,
        lastModified = lastModified,
        mimeType = mimeType ?: "audio/*",
        title = tags.title ?: fileName.substringBeforeLast("."),
        albumArtist = tags.albumArtist,
        artists = tags.artists,
        album = tags.album,
        track = tags.track,
        trackTotal = tags.trackTotal,
        disc = tags.disc,
        discTotal = tags.discTotal,
        duration = metadata?.audioProperties?.duration,
        year = tags.year,
        genres = tags.genres,
        replayGainTrack = tags.replayGainTrack,
        replayGainAlbum = tags.replayGainAlbum,
        lyrics = tags.lyrics,
        grouping = tags.grouping,
        bitRate = metadata?.audioProperties?.bitrate,
        bitDepth = null,
        sampleRate = metadata?.audioProperties?.sampleRate,
        channelCount = metadata?.audioProperties?.channelCount
    )
}

/**
 * The values of a file's tags, as TagLib read them. A null or empty field wasn't tagged: no fallback (such as the file
 * name for a missing title) is applied here.
 */
data class FileTags(
    val title: String?,
    val albumArtist: String?,
    val artists: List<String>,
    val album: String?,
    val track: Int?,
    val trackTotal: Int?,
    val disc: Int?,
    val discTotal: Int?,
    val year: String?,
    val genres: List<String>,
    val replayGainTrack: Double?,
    val replayGainAlbum: Double?,
    val lyrics: String?,
    val grouping: String?
)

/**
 * Maps a TagLib property map (keyed by [TagLibProperty] keys, whatever the container: ID3, Vorbis comments, MP4 atoms or
 * Matroska tags) to [FileTags].
 */
fun Map<String, List<String>>.toFileTags(): FileTags = mapValues { (_, values) -> values.map { it.decodeMisreadUtf8() } }.toFileTagsAsRead()

private fun Map<String, List<String>>.toFileTagsAsRead(): FileTags {
    fun first(property: TagLibProperty): String? = get(property.key)?.firstOrNull()
    val trackTag = first(TagLibProperty.Track)
    val discTag = first(TagLibProperty.Disc)
    return FileTags(
        title = first(TagLibProperty.Title),
        // TagLib passes tag names it doesn't know through unchanged, so a Matroska file tagged by ffmpeg has ALBUM_ARTIST
        albumArtist = first(TagLibProperty.AlbumArtist) ?: get(MATROSKA_ALBUM_ARTIST)?.firstOrNull(),
        artists =
            get(TagLibProperty.Artist.key).orEmpty().flatMap { artist ->
                artist.split(';')
                    .map { artist -> artist.trim() }
                    .filterNot { artist -> artist.isEmpty() }
            },
        album = first(TagLibProperty.Album),
        track = trackTag?.substringBefore('/')?.toIntOrNull(),
        trackTotal = trackTag?.substringAfter('/', "")?.toIntOrNull(),
        disc = discTag?.substringBefore('/')?.toIntOrNull(),
        discTotal = discTag?.substringAfter('/', "")?.toIntOrNull(),
        year =
            (first(TagLibProperty.Date) ?: first(TagLibProperty.OriginalDate))?.parseDate()
                ?: first(TagLibProperty.Year)?.parseDate(),
        genres =
            get(TagLibProperty.Genre.key).orEmpty().flatMap { genre ->
                genre.split(',', ';', '/')
                    .map { genre -> genre.trim() }
                    .filterNot { genre -> genre.isEmpty() }
            },
        replayGainTrack = getCaseInsensitive(TagLibProperty.ReplayGainTrack.key)?.firstOrNull()?.parseReplayGain(),
        replayGainAlbum = getCaseInsensitive(TagLibProperty.ReplayGainAlbum.key)?.firstOrNull()?.parseReplayGain(),
        lyrics = first(TagLibProperty.Lyrics),
        grouping = first(TagLibProperty.Grouping)
    )
}

/**
 * Undoes a common tagging fault: UTF-8 bytes in a tag declared Latin-1 (ID3v1 has no other encoding, and many taggers write
 * ID3v2 frames this way), which TagLib decodes a byte per character, so "Ñengo" reads as "Ã\u0091engo". Text made only of
 * Latin-1 characters whose bytes also form valid multi-byte UTF-8 is decoded as UTF-8 instead. Real Latin-1 text almost
 * never forms valid UTF-8: that takes an accented capital or lowercase letter followed by a symbol or control character.
 */
internal fun String.decodeMisreadUtf8(): String {
    if (none { it.code >= 0x80 } || any { it.code > 0xFF }) return this
    return try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(toByteArray(Charsets.ISO_8859_1)))
            .toString()
    } catch (e: CharacterCodingException) {
        this
    }
}

private const val MATROSKA_ALBUM_ARTIST = "ALBUM_ARTIST"

private fun String.parseReplayGain(): Double? = replace(oldValue = "db", newValue = "", ignoreCase = true).toDoubleOrNull()

fun String.parseDate(): String? {
    if (length < 4) {
        return null
    } else if (length > 4) {
        return substring(0, 4)
    }
    return this
}

fun <V> Map<String, V>.getCaseInsensitive(key: String): V? = get(key) ?: get(key.lowercase(Locale.US))
