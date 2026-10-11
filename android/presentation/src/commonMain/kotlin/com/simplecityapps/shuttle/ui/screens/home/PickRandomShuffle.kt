package com.simplecityapps.shuttle.ui.screens.home

import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.ui.actions.MediaAction
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import dev.zacsweers.metro.Inject
import kotlin.random.Random

enum class ShuffleBy { Artist, Genre, Year }

/** The group [PickRandomShuffle] chose, named for the user, and the action that shuffles its songs. */
data class RandomShuffle(val label: String, val action: MediaAction)

/**
 * Picks one random artist, genre or year from the songs in the library, with equal odds for each, and the action that
 * shuffles its songs. Only groups that have songs can be picked; null when the library has none to group by (an empty
 * library, or no song with a year). Artists are album artists, as the Artists list shows them.
 */
class PickRandomShuffle @Inject constructor(
    private val songRepository: SongRepository,
    private val random: Random = Random.Default,
) {
    suspend operator fun invoke(by: ShuffleBy): RandomShuffle? {
        val groups = songRepository.loadSongs(SongQuery.All()).groupSongs(by)
        if (groups.isEmpty()) return null
        val (label, songs) = groups.entries.elementAt(random.nextInt(groups.size))
        return RandomShuffle(label, MediaAction.Shuffle(MediaSelection.Songs(songs)))
    }

    private fun List<Song>.groupSongs(by: ShuffleBy): Map<String, List<Song>> = when (by) {
        ShuffleBy.Artist -> groupBy { it.albumArtist.orEmpty() }
        ShuffleBy.Genre -> flatMap { song -> song.genres.distinct().map { it to song } }.groupBy({ it.first }, { it.second })
        ShuffleBy.Year -> groupBy { it.date?.year?.toString().orEmpty() }
    }.filterKeys { it.isNotBlank() }
}
