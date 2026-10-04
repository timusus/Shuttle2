package com.simplecityapps.localmediaprovider.local.provider

import com.simplecityapps.shuttle.model.musicBrainzIds

/**
 * The values of a file's tags, as TagLib (Android) or FFmpeg (iOS) read them. A null or empty field wasn't tagged: no
 * fallback (such as the file name for a missing title) is applied here.
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
    val grouping: String?,
    // The raw tags of #637, never split: the ALBUMARTISTS and ARTISTS multi-value tags as written, the ARTIST tag as one
    // string (several values joined by "; "), COMPILATION, and the MusicBrainz ids.
    val albumArtists: List<String> = emptyList(),
    val artistsTag: List<String> = emptyList(),
    val artistDisplay: String? = null,
    val compilation: Boolean? = null,
    val mbTrackId: String? = null,
    val mbAlbumId: String? = null,
    val mbReleaseGroupId: String? = null,
    val mbArtistIds: List<String> = emptyList(),
    val mbAlbumArtistIds: List<String> = emptyList()
)

/**
 * Maps a TagLib property map (keyed by [TagLibProperty] keys, whatever the container: ID3, Vorbis comments, MP4 atoms or
 * Matroska tags) to [FileTags]. Android passes TagLib's own map; iOS passes FFmpeg's tags renamed by [ffmpegPropertyMap].
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
        track = trackTag?.substringBefore('/')?.trim()?.toIntOrNull(),
        trackTotal = trackTag?.substringAfter('/', "")?.trim()?.toIntOrNull(),
        disc = discTag?.substringBefore('/')?.trim()?.toIntOrNull(),
        discTotal = discTag?.substringAfter('/', "")?.trim()?.toIntOrNull(),
        // The original release's year beats a reissue's date; TagLib maps ID3's TDOR/TORY to ORIGINALDATE.
        year =
            first(TagLibProperty.OriginalDate)?.parseDate()
                ?: first(TagLibProperty.Date)?.parseDate()
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
        grouping = first(TagLibProperty.Grouping),
        albumArtists = values(TagLibProperty.AlbumArtists.key),
        artistsTag = values(TagLibProperty.Artists.key),
        artistDisplay = values(TagLibProperty.Artist.key).joinToString("; ").ifEmpty { null },
        compilation = first(TagLibProperty.Compilation)?.parseCompilation(),
        mbTrackId = musicBrainzIds(TagLibProperty.MusicBrainzTrackId).firstOrNull(),
        mbAlbumId = musicBrainzIds(TagLibProperty.MusicBrainzAlbumId).firstOrNull(),
        mbReleaseGroupId = musicBrainzIds(TagLibProperty.MusicBrainzReleaseGroupId).firstOrNull(),
        mbArtistIds = musicBrainzIds(TagLibProperty.MusicBrainzArtistId),
        mbAlbumArtistIds = musicBrainzIds(TagLibProperty.MusicBrainzAlbumArtistId)
    )
}

/** The non-blank values of the tag [key], trimmed and otherwise as written: a multi-value tag keeps its values, none split. */
private fun Map<String, List<String>>.values(key: String): List<String> = get(key).orEmpty().map { it.trim() }.filter { it.isNotEmpty() }

/**
 * The MusicBrainz ids tagged as [property], in order. TagLib names the ID3 TXXX frames it knows by [TagLibProperty.key],
 * and passes any other through as its description upper-cased, so the TXXX spelling is read too.
 */
private fun Map<String, List<String>>.musicBrainzIds(property: TagLibProperty): List<String> = musicBrainzIds(get(property.key) ?: MUSICBRAINZ_TXXX_NAMES[property]?.let { name -> get(name) }.orEmpty())

private val MUSICBRAINZ_TXXX_NAMES =
    mapOf(
        TagLibProperty.MusicBrainzTrackId to "MUSICBRAINZ TRACK ID",
        TagLibProperty.MusicBrainzAlbumId to "MUSICBRAINZ ALBUM ID",
        TagLibProperty.MusicBrainzReleaseGroupId to "MUSICBRAINZ RELEASE GROUP ID",
        TagLibProperty.MusicBrainzArtistId to "MUSICBRAINZ ARTIST ID",
        TagLibProperty.MusicBrainzAlbumArtistId to "MUSICBRAINZ ALBUM ARTIST ID"
    )

/** COMPILATION, TCMP and cpil hold "1" for a compilation ("0" for not); some taggers write "true". */
private fun String.parseCompilation(): Boolean? = when (trim().lowercase()) {
    "1", "true", "yes" -> true
    "0", "false", "no" -> false
    else -> null
}

/**
 * Undoes a common tagging fault: UTF-8 bytes in a tag declared Latin-1 (ID3v1 has no other encoding, and many taggers write
 * ID3v2 frames this way), which TagLib and FFmpeg decode a byte per character, so "Ñengo" reads as "Ã\u0091engo". Text
 * made only of Latin-1 characters whose bytes also form valid multi-byte UTF-8 is decoded as UTF-8 instead. Real Latin-1
 * text almost never forms valid UTF-8: that takes an accented capital or lowercase letter followed by a symbol or control
 * character.
 */
internal fun String.decodeMisreadUtf8(): String {
    if (none { it.code >= 0x80 } || any { it.code > 0xFF }) return this
    return try {
        ByteArray(length) { index -> this[index].code.toByte() }.decodeToString(throwOnInvalidSequence = true)
    } catch (e: CharacterCodingException) {
        this
    }
}

private const val MATROSKA_ALBUM_ARTIST = "ALBUM_ARTIST"

private fun String.parseReplayGain(): Double? = replace(oldValue = "db", newValue = "", ignoreCase = true).trim().toDoubleOrNull()

internal fun String.parseDate(): String? = take(4).takeIf { it.length == 4 && it.all { char -> char in '0'..'9' } }

private fun <V> Map<String, V>.getCaseInsensitive(key: String): V? = get(key) ?: get(key.lowercase())
