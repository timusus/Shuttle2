package com.simplecityapps.localmediaprovider.local.provider

import com.simplecityapps.mediaprovider.splitArtistTag
import com.simplecityapps.shuttle.model.Performer
import com.simplecityapps.shuttle.model.musicBrainzIds
import com.simplecityapps.shuttle.model.toTagYearDate
import kotlinx.datetime.LocalDate

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
    // Raw tags, unsplit: artists are split where they're read, with splitArtistTag.
    val albumArtists: List<String> = emptyList(),
    val artistsTag: List<String> = emptyList(),
    val artistDisplay: String? = null,
    val albumArtistDisplay: String? = null,
    val compilation: Boolean? = null,
    val mbTrackId: String? = null,
    val mbAlbumId: String? = null,
    val mbReleaseGroupId: String? = null,
    val mbArtistIds: List<String> = emptyList(),
    val mbAlbumArtistIds: List<String> = emptyList(),
    // The audio properties, which the tag map doesn't carry: set by a reader that has them (the MediaStore path's TagLib read)
    val bitRate: Int? = null,
    val bitDepth: Int? = null,
    val sampleRate: Int? = null,
    val channelCount: Int? = null,
    // Classical metadata; `grouping` stays the GROUPING tag (ID3 GRP1, MP4 ©grp), never the work
    val composer: String? = null,
    val composerSort: String? = null,
    val conductor: String? = null,
    val arranger: String? = null,
    val work: String? = null,
    val movementName: String? = null,
    val movementNumber: Int? = null,
    val movementTotal: Int? = null,
    val showMovement: Boolean? = null,
    val performers: List<Performer> = emptyList()
)

/** Whether [fileName] is a format whose tags are ID3 frames: MP3, and the AIFF and WAV chunks that embed ID3. */
fun carriesId3(fileName: String): Boolean = fileName.substringAfterLast('.', "").lowercase() in setOf("mp3", "aif", "aiff", "aifc", "wav")

/**
 * Maps a TagLib property map (keyed by [TagLibProperty] keys, whatever the container: ID3, Vorbis comments, MP4 atoms or
 * Matroska tags) to [FileTags]. Android passes TagLib's own map; iOS passes FFmpeg's tags renamed by [ffmpegPropertyMap].
 */
fun Map<String, List<String>>.toFileTags(id3: Boolean = false): FileTags = mapValues { (_, values) -> values.map { it.decodeMisreadUtf8() } }.toFileTagsAsRead(id3)

private fun Map<String, List<String>>.toFileTagsAsRead(id3: Boolean): FileTags {
    fun first(property: TagLibProperty): String? = get(property.key)?.firstOrNull()
    val trackTag = first(TagLibProperty.Track)
    val discTag = first(TagLibProperty.Disc)
    // Vorbis writes the movement number as MOVEMENT, ID3's MVIN may be "n/total", MP4 counts with MOVEMENTCOUNT
    val movementTag = first(TagLibProperty.MovementNumber) ?: get(VORBIS_MOVEMENT)?.firstOrNull()
    val movementName = first(TagLibProperty.MovementName)
    val showMovement = (first(TagLibProperty.ShowMovement) ?: get(MP4_SHOW_WORK_MOVEMENT)?.firstOrNull())?.parseCompilation()
    // ID3's TIT1 is a content group many taggers fill with a genre-like label, so on an MP3 it is only a work next to movement tags
    val isWork = !id3 || movementName != null || movementTag != null || showMovement == true
    return FileTags(
        title = first(TagLibProperty.Title),
        // A combined "A; B" album artist keeps only its first name, since an album has one album artist.
        albumArtist = (first(TagLibProperty.AlbumArtist) ?: get(MATROSKA_ALBUM_ARTIST)?.firstOrNull())?.let(::primaryAlbumArtist),
        artists =
            get(TagLibProperty.Artist.key).orEmpty().flatMap(::splitArtistTag),
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
                genre.split(*GENRE_SEPARATORS)
                    .map { genre -> genre.trim() }
                    .filterNot { genre -> genre.isEmpty() }
            },
        replayGainTrack = getCaseInsensitive(TagLibProperty.ReplayGainTrack.key)?.firstOrNull()?.parseReplayGain() ?: getCaseInsensitive(R128_TRACK_GAIN)?.firstOrNull()?.parseR128Gain(),
        replayGainAlbum = getCaseInsensitive(TagLibProperty.ReplayGainAlbum.key)?.firstOrNull()?.parseReplayGain() ?: getCaseInsensitive(R128_ALBUM_GAIN)?.firstOrNull()?.parseR128Gain(),
        lyrics = first(TagLibProperty.Lyrics),
        grouping = first(TagLibProperty.Grouping),
        // A multi-value ALBUMARTIST (repeated Vorbis fields, null-separated ID3v2.4, several MP4 aART) lists its values when
        // there is no ALBUMARTISTS tag; a single value stays only in albumArtist.
        albumArtists = values(TagLibProperty.AlbumArtists.key).ifEmpty { values(TagLibProperty.AlbumArtist.key).takeIf { it.size > 1 }.orEmpty() },
        artistsTag = values(TagLibProperty.Artists.key),
        artistDisplay = values(TagLibProperty.Artist.key).joinToString("; ").ifEmpty { null },
        albumArtistDisplay = (first(TagLibProperty.AlbumArtist) ?: get(MATROSKA_ALBUM_ARTIST)?.firstOrNull())?.takeIf { it.isNotBlank() },
        compilation = first(TagLibProperty.Compilation)?.parseCompilation(),
        mbTrackId = musicBrainzIds(TagLibProperty.MusicBrainzTrackId).firstOrNull(),
        mbAlbumId = musicBrainzIds(TagLibProperty.MusicBrainzAlbumId).firstOrNull(),
        mbReleaseGroupId = musicBrainzIds(TagLibProperty.MusicBrainzReleaseGroupId).firstOrNull(),
        mbArtistIds = musicBrainzIds(TagLibProperty.MusicBrainzArtistId),
        mbAlbumArtistIds = musicBrainzIds(TagLibProperty.MusicBrainzAlbumArtistId),
        composer = first(TagLibProperty.Composer),
        composerSort = first(TagLibProperty.ComposerSort),
        conductor = first(TagLibProperty.Conductor),
        arranger = first(TagLibProperty.Arranger),
        work = first(TagLibProperty.Work)?.takeIf { isWork },
        movementName = movementName,
        movementNumber = movementTag?.substringBefore('/')?.trim()?.toIntOrNull(),
        movementTotal =
            (first(TagLibProperty.MovementTotal) ?: get(MP4_MOVEMENT_COUNT)?.firstOrNull() ?: movementTag?.substringAfter('/', ""))
                ?.trim()?.toIntOrNull(),
        showMovement = showMovement,
        performers = performers()
    )
}

/**
 * The performers tagged: Vorbis/MP4 "Name (instrument)" values of PERFORMER, and ID3 TMCL, which TagLib exposes as
 * "PERFORMER:<instrument>" with the name as the value. The instrument is the role; a bare name has none.
 */
private fun Map<String, List<String>>.performers(): List<Performer> = entries.flatMap { (key, values) ->
    when {
        key == TagLibProperty.Performer.key -> values.mapNotNull(::parsePerformer)

        key.startsWith("${TagLibProperty.Performer.key}:") -> {
            val role = key.substringAfter(':').trim().ifEmpty { null }
            values.map { it.trim() }.filter { it.isNotEmpty() }.map { name -> Performer(name, role) }
        }

        else -> emptyList()
    }
}

/**
 * "Name (instrument)" is the name and the instrument, the instrument itself may hold parentheses ("Name (violin (solo))");
 * anything else is a name alone. A blank value is no performer.
 */
internal fun parsePerformer(value: String): Performer? {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return null
    val open = matchingOpenParen(trimmed)
    if (open == null || open == 0 || trimmed[open - 1] != ' ') return Performer(trimmed)
    val role = trimmed.substring(open + 1, trimmed.length - 1).trim()
    return Performer(trimmed.substring(0, open).trim(), role.ifEmpty { null })
}

/** The index of the '(' that pairs with the ')' ending [value], or null when [value] doesn't end in a balanced group. */
private fun matchingOpenParen(value: String): Int? {
    if (!value.endsWith(')')) return null
    var depth = 0
    for (i in value.indices.reversed()) {
        when (value[i]) {
            ')' -> depth++
            '(' -> if (--depth == 0) return i
        }
    }
    return null
}

private const val VORBIS_MOVEMENT = "MOVEMENT"
private const val MP4_MOVEMENT_COUNT = "MOVEMENTCOUNT"
private const val MP4_SHOW_WORK_MOVEMENT = "SHOWWORKMOVEMENT"

/** The first artist an ALBUMARTIST value names, split as [splitArtistTag] splits an ARTIST value; a value naming none (blank) is null. */
internal fun primaryAlbumArtist(value: String): String? = splitArtistTag(value).firstOrNull()

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

/** A genre tag splits on these; '/' is not one, so "R&B/Soul" stays whole. The tag editor's `Song.edited` repeats this list. */
private val GENRE_SEPARATORS = charArrayOf(';', ',')

internal const val R128_TRACK_GAIN = "R128_TRACK_GAIN"
internal const val R128_ALBUM_GAIN = "R128_ALBUM_GAIN"

/** An Opus R128 gain is a Q7.8 fixed-point dB offset from -23 LUFS; ReplayGain's reference is -18 LUFS, 5 dB louder. */
private fun String.parseR128Gain(): Double? = trim().toIntOrNull()?.let { it / 256.0 + 5.0 }

private fun String.parseReplayGain(): Double? = replace(oldValue = "db", newValue = "", ignoreCase = true).trim().toDoubleOrNull()

/** The date [parseDate] read, as the first of its year: songs store the year only. */
fun String.toYearDate(): LocalDate? = parseDate()?.toIntOrNull().toTagYearDate()

internal fun String.parseDate(): String? = trim().take(4).takeIf { it.length == 4 && it.all { char -> char in '0'..'9' } }

private fun <V> Map<String, V>.getCaseInsensitive(key: String): V? = get(key) ?: get(key.lowercase())
