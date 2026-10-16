package com.simplecityapps.shuttle.model

import com.simplecityapps.mediaprovider.search.SearchText
import dev.zacsweers.metro.Inject

/** One album's movements of a work: another recording of it, as [FindOtherRecordings] finds them. */
data class OtherRecording(
    val groupKey: AlbumGroupKey,
    val album: String?,
    val albumArtist: String?,
    /** The album's songs that belong to the work, in disc and track order. */
    val songs: List<Song>,
) {
    val year: Int? get() = songs.firstNotNullOfOrNull { it.date?.year }

    val leadPerformer: String? get() = songs.firstNotNullOfOrNull { it.leadPerformer() }
}

/**
 * Other recordings of a work in the library, one per album and never the work's own album. A song belongs to the same
 * work when its composer is the same person and either the catalogue reference (Op. 67, BWV 1041) or the whole work
 * title is the same, ignoring case, punctuation and diacritics. The library is read once per call, so one call serves
 * every work of an album.
 *
 * Songs carry no MusicBrainz work id, so that tier of matching does not exist yet.
 */
@Inject
class FindOtherRecordings {
    /** The recordings of each of [works], keyed by the id of the work's first song; a work with none is absent. */
    operator fun invoke(works: List<WorkBlock>, library: List<Song>): Map<Long, List<OtherRecording>> {
        if (works.isEmpty()) return emptyMap()
        val bySurname = library
            .filter { !it.composer.isNullOrBlank() && it.workName() != null }
            .groupBy { composerSurnameKey(it.composer.orEmpty()).foldDiacritics() }
        return works.mapNotNull { work ->
            val composer = work.composer ?: return@mapNotNull null
            val own = work.songs.first().albumGroupKey
            val identity = WorkIdentity(work.work)
            val recordings = bySurname[composerSurnameKey(composer).foldDiacritics()].orEmpty()
                .filter { song ->
                    song.albumGroupKey != own &&
                        sameComposer(song.composer?.foldDiacritics(), composer.foldDiacritics()) &&
                        identity.matches(song.workName().orEmpty())
                }
                .groupBy { it.albumGroupKey }
                .map { (key, songs) ->
                    val ordered = songs.sortedWith(compareBy({ it.disc ?: 1 }, { it.track ?: 0 }))
                    OtherRecording(key, ordered.first().album, ordered.first().albumArtist, ordered)
                }
                .sortedWith(compareByDescending<OtherRecording> { it.year ?: 0 }.thenBy { it.album.orEmpty().lowercase() })
            if (recordings.isEmpty()) null else work.songs.first().id to recordings
        }.toMap()
    }
}

private class WorkIdentity(work: String) {
    private val keys = catalogueSearchKeys(work).toSet()
    private val catalogue = parseWorkTitle(work).catalogue?.normalised()
    private val title = work.foldDiacritics().normalised()

    fun matches(other: String): Boolean {
        if (keys.isNotEmpty() && catalogueSearchKeys(other).any { it in keys } && parseWorkTitle(other).catalogue?.normalised() == catalogue) return true
        return other.foldDiacritics().normalised() == title
    }
}

private fun String.foldDiacritics(): String = buildString {
    for (c in this@foldDiacritics) {
        if (c.code < 128) append(c) else append(SearchText.fold(c) ?: c.toString())
    }
}
