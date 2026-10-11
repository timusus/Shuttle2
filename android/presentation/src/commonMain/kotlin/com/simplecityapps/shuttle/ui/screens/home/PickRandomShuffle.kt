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
 *
 * An artist's songs resolve when the action runs, as the artist page shows them; `SongQuery` has no genre or year
 * variant, so those picks carry their songs.
 */
class PickRandomShuffle @Inject constructor(
    private val songRepository: SongRepository,
) {
    private var random: Random = Random.Default

    /** A seeded [random] for tests, so the graph doesn't rely on Metro's handling of a defaulted parameter. */
    internal constructor(songRepository: SongRepository, random: Random) : this(songRepository) {
        this.random = random
    }

    suspend operator fun invoke(by: ShuffleBy): RandomShuffle? {
        val songs = songRepository.loadSongs(SongQuery.All())
        return when (by) {
            ShuffleBy.Artist -> pickArtist(songs)
            ShuffleBy.Genre -> pickSongs(songs.flatMap { song -> song.genres.distinct().map { it to song } }.groupBy({ it.first }, { it.second }))
            ShuffleBy.Year -> pickSongs(songs.groupBy { it.date?.year?.toString().orEmpty() })
        }
    }

    private fun pickArtist(songs: List<Song>): RandomShuffle? {
        val artists = songs.filter { !it.albumArtist.isNullOrBlank() }.associateBy({ it.albumArtistGroupKey }, { it.albumArtist.orEmpty() })
        if (artists.isEmpty()) return null
        val (key, label) = artists.entries.elementAt(random.nextInt(artists.size))
        return RandomShuffle(label, MediaAction.Shuffle(MediaSelection.SongsMatching(SongQuery.ArtistGroupKey(key))))
    }

    private fun pickSongs(groups: Map<String, List<Song>>): RandomShuffle? {
        val named = groups.filterKeys { it.isNotBlank() }
        if (named.isEmpty()) return null
        val (label, songs) = named.entries.elementAt(random.nextInt(named.size))
        return RandomShuffle(label, MediaAction.Shuffle(MediaSelection.Songs(songs)))
    }
}
