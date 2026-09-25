package com.simplecityapps.shuttle.ui.screens.tageditor

import com.simplecityapps.localmediaprovider.local.provider.TagLibProperty
import com.simplecityapps.mediaprovider.model.AudioFile
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.shuttle.model.Song
import javax.inject.Inject
import kotlinx.datetime.LocalDate

data class TagWriteResult(
    /** The songs whose files were written, as the library now has them. */
    val updated: List<Song>,
    /** The songs whose files couldn't be written; the library keeps their old tags. */
    val failed: List<Song>,
)

/**
 * Writes the changed fields to each song's file, then brings the library and the queue up to date for the songs
 * whose write succeeded. Fields the user didn't change are left as they are in every file.
 */
class WriteSongTags @Inject constructor(
    private val tagFileAccess: TagFileAccess,
    private val songRepository: SongRepository,
    private val playbackOperations: PlaybackOperations,
) {
    suspend operator fun invoke(
        songs: List<EditableSong>,
        edits: Map<TagField, String>,
        onProgress: (written: Int, total: Int) -> Unit = { _, _ -> },
    ): TagWriteResult {
        if (edits.isEmpty()) return TagWriteResult(updated = emptyList(), failed = emptyList())
        val updated = mutableListOf<Song>()
        val failed = mutableListOf<Song>()
        songs.forEachIndexed { index, (song, file) ->
            onProgress(index, songs.size)
            if (tagFileAccess.write(song, metadata(file, edits))) updated += song.edited(edits) else failed += song
        }
        onProgress(songs.size, songs.size)
        if (updated.isNotEmpty()) {
            songRepository.update(updated)
            playbackOperations.updateQueueSongs(updated)
        }
        return TagWriteResult(updated, failed)
    }
}

/**
 * The TagLib properties to write to [file] for [edits]. Track and disc numbers are written with their totals as
 * "03/12"; when only a total changes, each file keeps its own number. A field emptied by the user maps to an empty
 * list, which KTagLib removes from the tag.
 */
internal fun metadata(
    file: AudioFile,
    edits: Map<TagField, String>,
): Map<String, List<String>> = buildMap {
    edits[TagField.Title]?.let { put(TagLibProperty.Title.key, tagValues(it)) }
    edits[TagField.Artists]?.let { put(TagLibProperty.Artist.key, tagValues(it)) }
    edits[TagField.Album]?.let { put(TagLibProperty.Album.key, tagValues(it)) }
    edits[TagField.AlbumArtist]?.let { put(TagLibProperty.AlbumArtist.key, tagValues(it)) }
    edits[TagField.Year]?.let { put(TagLibProperty.Date.key, tagValues(it)) }
    numberWithTotal(edits, TagField.Track, TagField.TrackTotal, file.track, file.trackTotal)?.let { put(TagLibProperty.Track.key, tagValues(it)) }
    numberWithTotal(edits, TagField.Disc, TagField.DiscTotal, file.disc, file.discTotal)?.let { put(TagLibProperty.Disc.key, tagValues(it)) }
    edits[TagField.Genres]?.let { put(TagLibProperty.Genre.key, tagValues(it)) }
    edits[TagField.Lyrics]?.let { put(TagLibProperty.Lyrics.key, tagValues(it)) }
}

private fun tagValues(value: String): List<String> = if (value.isBlank()) emptyList() else listOf(value)

private fun numberWithTotal(
    edits: Map<TagField, String>,
    numberField: TagField,
    totalField: TagField,
    fileNumber: Int?,
    fileTotal: Int?,
): String? {
    if (numberField !in edits && totalField !in edits) return null
    val number = (edits[numberField] ?: fileNumber?.toString()).orEmpty().trim()
    // Without a number there's nothing to hang a total on; an emptied number clears the tag.
    if (number.isEmpty()) return if (numberField in edits) "" else null
    val total = (edits[totalField] ?: fileTotal?.toString()).orEmpty().trim()
    return number.padStart(2, '0') + if (total.isEmpty()) "" else "/${total.padStart(2, '0')}"
}

/** [this] song with [edits] applied, as the library stores it. */
internal fun Song.edited(edits: Map<TagField, String>): Song {
    fun text(field: TagField, current: String?) = if (field in edits) edits.getValue(field).trim().ifEmpty { null } else current
    fun list(field: TagField, current: List<String>) = if (field in edits) edits.getValue(field).split(",").map { it.trim() }.filter { it.isNotEmpty() } else current
    fun number(field: TagField, current: Int?) = if (field in edits) edits.getValue(field).trim().toIntOrNull() else current
    return copy(
        name = text(TagField.Title, name),
        artists = list(TagField.Artists, artists),
        album = text(TagField.Album, album),
        albumArtist = text(TagField.AlbumArtist, albumArtist),
        date = if (TagField.Year in edits) edits.getValue(TagField.Year).trim().toIntOrNull()?.let { LocalDate(it, 1, 1) } else date,
        track = number(TagField.Track, track),
        disc = number(TagField.Disc, disc),
        genres = list(TagField.Genres, genres),
        lyrics = text(TagField.Lyrics, lyrics),
    )
}
