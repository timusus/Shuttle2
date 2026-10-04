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
 */
fun ffmpegPropertyMap(tags: List<Pair<String, String>>): Map<String, List<String>> {
    val byName = LinkedHashMap<String, String>()
    for ((key, value) in tags) {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) continue
        val name = key.lowercase().filter { it.isLetterOrDigit() }
        // ID3's USLT arrives as "lyrics-eng", one key per language.
        byName.getOrPut(if (name.startsWith("lyrics")) "lyrics" else name) { trimmed }
    }
    return FFMPEG_NAMES.mapNotNull { (property, names) ->
        val values = names.mapNotNull(byName::get)
            .flatMap { value -> if (property in MULTI_VALUE) value.split(';').map { it.trim() }.filter { it.isNotEmpty() } else listOf(value) }
        values.takeIf { it.isNotEmpty() }?.let { property.key to it }
    }.toMap()
}

private val MULTI_VALUE = setOf(TagLibProperty.Artists, TagLibProperty.AlbumArtists)

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
        TagLibProperty.MusicBrainzAlbumArtistId to listOf("musicbrainzalbumartistid")
    )
