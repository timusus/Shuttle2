package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.mediaprovider.AggregateSimilarSongsProvider
import com.simplecityapps.mediaprovider.repository.genres.GenreRepository
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.model.PlayContext
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import dev.zacsweers.metro.Inject
import kotlin.random.Random
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Replaces the queue with a radio built from a song, album or artist: the server's mix for the seed where its provider
 * offers one, else (and when the mix holds nothing in the library) songs by the seed's artists and in its genres,
 * shuffled. A single song seed plays first.
 */
@Inject
class StartRadio(
    private val songRepository: SongRepository,
    private val genreRepository: GenreRepository,
    private val resolveSongs: ResolveSongs,
    private val similarSongsProvider: AggregateSimilarSongsProvider,
    private val playSongs: PlaySongs,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val random: Random = Random.Default,
) {
    sealed interface Result {
        data object Success : Result
        data object NoSongs : Result
        data class Failure(val message: String?) : Result
    }

    suspend operator fun invoke(selection: MediaSelection): Result {
        val seedSongs = resolveSongs(selection)
        val representative = seedSongs.firstOrNull() ?: return Result.NoSongs
        val startsWith = representative.takeIf { selection is MediaSelection.Songs && seedSongs.size == 1 }

        val mix = serverMix(representative, seedSongs).ifEmpty { localMix(seedSongs) }
        if (mix.isEmpty()) return Result.NoSongs

        return when (val result = playSongs(listOfNotNull(startsWith) + mix, 0, PlayContext.None)) {
            is PlaySongs.Result.Success -> Result.Success
            is PlaySongs.Result.Failure -> Result.Failure(result.message)
        }
    }

    private suspend fun serverMix(seed: Song, seedSongs: List<Song>): List<Song> {
        if (!similarSongsProvider.handles(seed)) return emptyList()
        val ids = similarSongsProvider.similarSongIds(seed, LIMIT + seedSongs.size)
        if (ids.isEmpty()) return emptyList()
        val found = songRepository.loadSongs(SongQuery.ExternalIds(ids, seed.mediaProvider)).associateBy { it.externalId }
        val seedIds = seedSongs.map { it.id }.toSet()
        return ids.mapNotNull { found[it] }.filter { it.id !in seedIds }.take(LIMIT)
    }

    private suspend fun localMix(seedSongs: List<Song>): List<Song> {
        val artists = seedSongs.flatMap { it.albumArtistKeys }.distinct()
        val genres = seedSongs.flatMap { it.genres }.distinct()
        val byArtist = if (artists.isEmpty()) emptyList() else songRepository.loadSongs(SongQuery.ArtistGroupKeys(artists.map { SongQuery.ArtistGroupKey(it) }))
        val byGenre = if (genres.isEmpty()) emptyList() else genreRepository.getSongsForGenres(genres, SongQuery.All()).first()
        return withContext(defaultDispatcher) {
            val seedIds = seedSongs.map { it.id }.toSet()
            (byArtist + byGenre)
                .distinctBy { it.id }
                .filter { it.id !in seedIds }
                .shuffled(random)
                .take(LIMIT)
        }
    }

    private companion object {
        const val LIMIT = 50
    }
}
