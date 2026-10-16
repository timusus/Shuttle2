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
 * work when it passes the strict classical test, its composer is the same person and either the catalogue reference
 * (Op. 67, BWV 1041) or the whole work title is the same, ignoring case, punctuation and diacritics. The library is
 * read once per call, so one call serves every work of an album; a work that isn't strictly classical finds nothing.
 *
 * Songs carry no MusicBrainz work id, so that tier of matching does not exist yet.
 */
@Inject
class FindOtherRecordings {
    private class Candidate(val song: Song, val composer: String, val work: String)

    /** The recordings of each of [works], keyed by the id of the work's first song; a work with none is absent. */
    operator fun invoke(works: List<WorkBlock>, library: List<Song>): Map<Long, List<OtherRecording>> {
        if (works.isEmpty()) return emptyMap()
        val bySurname = library
            .mapNotNull { song ->
                val composer = song.composer?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                if (!listOf(song).isClassicalBlock()) return@mapNotNull null
                Candidate(song, composer.foldDiacritics(), song.workName() ?: return@mapNotNull null)
            }
            .groupBy { composerSurnameKey(it.composer) }
        return works.mapNotNull { work ->
            val composer = work.composer?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            if (!work.songs.isClassicalBlock()) return@mapNotNull null
            val first = work.songs.first()
            val own = first.albumGroupKey
            val ownAlbum = first.album.orEmpty().normalised()
            val ownYear = first.date?.year
            val ownMovements = work.songs.mapTo(HashSet()) { it.name.orEmpty().normalised() }
            val identity = WorkIdentity(work.work)
            val recordings = bySurname[composerSurnameKey(composer.foldDiacritics())].orEmpty()
                .filter { candidate ->
                    candidate.song.albumGroupKey != own &&
                        sameComposer(candidate.composer, composer.foldDiacritics()) &&
                        identity.matches(candidate.work)
                }
                .map { it.song }
                .groupBy { it.albumGroupKey }
                .filterValues { songs ->
                    // The same album imported twice (local and streaming copy, or split by album artist) has another group key
                    val copy = songs.first().album.orEmpty().normalised() == ownAlbum &&
                        songs.first().date?.year == ownYear &&
                        songs.mapTo(HashSet()) { it.name.orEmpty().normalised() } == ownMovements
                    !copy
                }
                .map { (key, songs) ->
                    val ordered = songs.sortedWith(compareBy({ it.disc ?: 1 }, { it.track ?: 0 }))
                    OtherRecording(key, ordered.first().album, ordered.first().albumArtist, ordered)
                }
                .sortedWith(compareByDescending<OtherRecording> { it.year ?: 0 }.thenBy { it.album.orEmpty().lowercase() })
            if (recordings.isEmpty()) null else work.songs.first().id to recordings
        }.toMap()
    }
}

internal class WorkIdentity(work: String) {
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
