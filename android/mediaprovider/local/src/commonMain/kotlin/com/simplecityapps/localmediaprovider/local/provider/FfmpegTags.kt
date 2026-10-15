package com.simplecityapps.localmediaprovider.local.provider

/**
 * Renames the tags libavformat read from a file (iOS's reader, `tag_read.c`) to the TagLib property map [toFileTags] maps,
 * so both platforms share one set of rules. [tags] are in reading order, the container's before the stream's.
 *
 * libavformat names a tag as its container spells it, in any case ("album_artist", "ALBUMARTIST", "MusicBrainz Album
 * Id"), so a name is matched with its case and everything but letters and digits dropped; a name read twice keeps its
 * first non-blank value, the container's over the stream's. Where several names hold one property (TRACKNUMBER is
 * "track" or "tracknumber"; ORIGINALDATE is Vorbis/MP4's "originaldate" or ID3's raw "TDOR" and "TORY"), the property's
 * values are theirs in that order, so [toFileTags] reads the first. libavformat joins a repeated Vorbis comment's values
 * with ';', so the multi-value ARTISTS and ALBUMARTISTS tags are split back into TagLib's list. Tags with no property
 * are dropped.
 *
 * ffmpeg renames ID3's TIT1 (the work) to "grouping" and TPE3 (the conductor) to "performer", which Vorbis and MP4 use for
 * a grouping and a performer. [id3] says the file is ID3-tagged (an MP3), so those two names read as the work and the
 * conductor, and the raw GRP1 frame is the grouping.
 */
fun ffmpegPropertyMap(
    tags: List<Pair<String, String>>,
    id3: Boolean = false
): Map<String, List<String>> {
    val byName = LinkedHashMap<String, String>()
    for ((key, value) in tags) {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) continue
        val name = key.lowercase().filter { it.isLetterOrDigit() }
        // ID3's USLT arrives as "lyrics-eng", one key per language.
        byName.getOrPut(if (name.startsWith("lyrics")) "lyrics" else name) { trimmed }
    }
    val r128 = R128_NAMES.mapNotNull { (name, key) -> byName[name]?.let { key to listOf(it) } }
    return r128.toMap() + (if (id3) ID3_NAMES else FFMPEG_NAMES).mapNotNull { (property, names) ->
        val values = names.mapNotNull(byName::get)
            .flatMap { value -> if (property in MULTI_VALUE) value.split(';').map { it.trim() }.filter { it.isNotEmpty() } else listOf(value) }
        values.takeIf { it.isNotEmpty() }?.let { property.key to it }
    }.toMap()
}

/** Opus's R128 gains have no [TagLibProperty]; [toFileTags] reads them by their Vorbis keys. */
private val R128_NAMES = listOf("r128trackgain" to R128_TRACK_GAIN, "r128albumgain" to R128_ALBUM_GAIN)

private val MULTI_VALUE = setOf(TagLibProperty.Artists, TagLibProperty.AlbumArtists, TagLibProperty.Performer)

/** Each property's names as libavformat spells them, normalised as [ffmpegPropertyMap] matches them, in precedence order. */
private val FFMPEG_NAMES: Map<TagLibProperty, List<String>> =
    mapOf(
        TagLibProperty.Title to listOf("title"),
        TagLibProperty.Artist to listOf("artist"),
        TagLibProperty.Artists to listOf("artists"),
        TagLibProperty.AlbumArtist to listOf("albumartist"),
        TagLibProperty.AlbumArtists to listOf("albumartists"),
        TagLibProperty.Album to listOf("album"),
        TagLibProperty.Track to listOf("track", "tracknumber"),
        TagLibProperty.Disc to listOf("disc", "discnumber"),
        // The original release's year beats a reissue's: ffmpeg names ID3's TDOR/TORY raw, and Vorbis/MP4's ORIGINALDATE as is.
        TagLibProperty.OriginalDate to listOf("originaldate", "tdor", "tory"),
        TagLibProperty.Date to listOf("date"),
        TagLibProperty.Year to listOf("year"),
        TagLibProperty.Genre to listOf("genre"),
        TagLibProperty.ReplayGainTrack to listOf("replaygaintrackgain"),
        TagLibProperty.ReplayGainAlbum to listOf("replaygainalbumgain"),
        TagLibProperty.Lyrics to listOf("lyrics", "unsyncedlyrics"),
        TagLibProperty.Grouping to listOf("grouping", "contentgroup"),
        TagLibProperty.Compilation to listOf("compilation"),
        TagLibProperty.MusicBrainzTrackId to listOf("musicbrainztrackid"),
        TagLibProperty.MusicBrainzAlbumId to listOf("musicbrainzalbumid"),
        TagLibProperty.MusicBrainzReleaseGroupId to listOf("musicbrainzreleasegroupid"),
        TagLibProperty.MusicBrainzArtistId to listOf("musicbrainzartistid"),
        TagLibProperty.MusicBrainzAlbumArtistId to listOf("musicbrainzalbumartistid"),
        TagLibProperty.Composer to listOf("composer"),
        TagLibProperty.ComposerSort to listOf("composersort", "sortcomposer", "tsoc"),
        TagLibProperty.Conductor to listOf("conductor"),
        TagLibProperty.Arranger to listOf("arranger"),
        TagLibProperty.Work to listOf("work"),
        TagLibProperty.MovementName to listOf("movementname", "mvnm"),
        // Vorbis spells the number MOVEMENT; ID3's MVIN may be "n/total"
        TagLibProperty.MovementNumber to listOf("movementnumber", "movement", "mvin"),
        TagLibProperty.MovementTotal to listOf("movementtotal"),
        TagLibProperty.ShowMovement to listOf("showmovement"),
        TagLibProperty.Performer to listOf("performer")
    )

/** [FFMPEG_NAMES] for an ID3-tagged file: see [ffmpegPropertyMap]. */
private val ID3_NAMES: Map<TagLibProperty, List<String>> =
    FFMPEG_NAMES + mapOf(
        TagLibProperty.Grouping to listOf("grp1"),
        TagLibProperty.Work to listOf("work", "grouping", "contentgroup", "tit1"),
        TagLibProperty.Conductor to listOf("conductor", "performer"),
        TagLibProperty.Performer to emptyList()
    )
