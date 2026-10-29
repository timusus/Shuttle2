package com.simplecityapps.shuttle.model

/** One labelled pair of the song-match corpus (support/song-match): two copies' tags and whether they're one song. */
data class SongCopyPair(
    val category: String,
    val same: Boolean,
    val a: SongCopyTags,
    val b: SongCopyTags
) {
    /** MusicBrainz can't say whether these are one song (likely unmerged duplicate recordings): kept out of the rates. */
    val ambiguous: Boolean get() = category.startsWith("ambiguous_")
}

/** The pairs of a corpus TSV, as make_pairs.py writes it. */
fun parseSongCopyPairs(tsv: String): List<SongCopyPair> = tsv.lineSequence().drop(1).filter { it.isNotBlank() }.map { line ->
    val fields = line.split('\t')

    fun tags(offset: Int) = SongCopyTags(
        title = fields[offset].ifEmpty { null },
        album = fields[offset + 1].ifEmpty { null },
        albumArtist = fields[offset + 2].ifEmpty { null },
        artist = fields[offset + 3].ifEmpty { null },
        disc = fields[offset + 4].toIntOrNull(),
        track = fields[offset + 5].toIntOrNull(),
        durationMs = fields[offset + 6].toIntOrNull(),
        year = fields[offset + 7].toIntOrNull()
    )
    SongCopyPair(category = fields[1], same = fields[2] == "same", a = tags(3), b = tags(11))
}.toList()

/** A file under commonTest/resources. */
expect fun readSongCopyResource(path: String): String
