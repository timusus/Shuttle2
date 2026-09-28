package com.simplecityapps.localmediaprovider.local.repository

import com.simplecityapps.localmediaprovider.local.data.room.dao.AlbumTaggingRow
import com.simplecityapps.localmediaprovider.local.data.room.dao.SuggestionsDao
import com.simplecityapps.localmediaprovider.local.data.room.dao.toSong
import com.simplecityapps.mediaprovider.repository.suggestions.ImportDays
import com.simplecityapps.mediaprovider.repository.suggestions.SuggestionsRepository
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.AlbumArtistGroupKey
import com.simplecityapps.shuttle.model.AlbumGroupKey
import com.simplecityapps.shuttle.model.Genre
import com.simplecityapps.shuttle.model.albumArtistGroupKeyOf
import com.simplecityapps.shuttle.model.albumGroupKeyOf
import com.simplecityapps.shuttle.model.removeArticles
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * [SuggestionsRepository] over the songs table. A lookup by group key finds the tagged names that make the key (from
 * the distinct names, not the songs), then reads only those names' songs and groups them as the album and artist
 * repositories do, with the same key functions, so a key found here is one they have.
 */
class LocalSuggestionsRepository(
    private val suggestionsDao: SuggestionsDao
) : SuggestionsRepository {
    override fun songCount(): Flow<Int> = suggestionsDao.songCount()

    override suspend fun albums(keys: List<AlbumGroupKey>): List<Album> {
        val wanted = keys.filter { it.key != null }.toSet()
        if (wanted.isEmpty()) return emptyList()
        val albumParts = wanted.mapNotNull { it.key }.toSet()
        val names = suggestionsDao.albumNames().filter { name -> name.lowercase().removeArticles() in albumParts }
        if (names.isEmpty()) return emptyList()
        val albums = names.chunked(MAX_BOUND_VARIABLES)
            .flatMap { chunk -> suggestionsDao.songsInAlbums(chunk) }
            .map { it.toSong() }
            .groupBy { it.albumGroupKey }
            .filterKeys { it in wanted }
            .mapValues { (key, songs) -> songs.toAlbum(key) }
        return keys.mapNotNull { albums[it] }.distinct()
    }

    override suspend fun albumArtists(keys: List<AlbumArtistGroupKey>): List<AlbumArtist> {
        val wanted = keys.toSet()
        if (wanted.isEmpty()) return emptyList()
        val taggings = suggestionsDao.artistTaggings().filter { albumArtistGroupKeyOf(it.albumArtist, it.artists) in wanted }
        if (taggings.isEmpty()) return emptyList()
        val albumArtists = taggings.mapNotNull { it.albumArtist }.distinct()
        // As stored: the artists column holds them joined by ';'
        val artists = taggings.filter { it.albumArtist == null }.map { it.artists.joinToString(";") }.distinct()
        val songs = suggestionsDao.songsByArtists(albumArtists.take(MAX_BOUND_VARIABLES / 2), artists.take(MAX_BOUND_VARIABLES / 2))
            .map { it.toSong() }
            .groupBy { it.albumArtistGroupKey }
            .filterKeys { it in wanted }
            .mapValues { (key, songs) -> songs.toAlbumArtist(key) }
        return keys.mapNotNull { songs[it] }.distinct()
    }

    override suspend fun genres(names: List<String>): List<Genre> {
        val genres = genreTotals()
        return names.distinct().mapNotNull { genres[it] }
    }

    override suspend fun largestGenres(
        minSongs: Int,
        limit: Int
    ): List<Genre> = genreTotals().values
        .filter { it.songCount >= minSongs }
        .sortedWith(compareByDescending<Genre> { it.songCount }.thenBy { it.name })
        .take(limit)

    override suspend fun recentlyCompletedAlbums(limit: Int): List<AlbumGroupKey> = suggestionsDao.recentlyCompletedAlbums(limit).albumKeys()

    override suspend fun recentlyAddedAlbums(
        since: Instant,
        limit: Int
    ): List<AlbumGroupKey> = suggestionsDao.recentlyAddedAlbums(since, limit).albumKeys()

    override suspend fun albumsToRediscover(
        minPlays: Int,
        playedBefore: Instant,
        limit: Int
    ): List<AlbumGroupKey> = suggestionsDao.albumsToRediscover(minPlays, playedBefore, limit).albumKeys()

    override suspend fun importDays(): ImportDays = ImportDays(songs = suggestionsDao.countSongs(), largestDay = suggestionsDao.largestImportDay() ?: 0)

    /** Each genre's songs, summed over the genre taggings: a song tagged with several genres counts for each. */
    private suspend fun genreTotals(): Map<String, Genre> {
        val totals = linkedMapOf<String, Genre>()
        suggestionsDao.genreTaggings().forEach { row ->
            row.genres.filter { it.isNotBlank() }.distinct().forEach { name ->
                val genre = totals[name] ?: Genre(name, 0, 0, emptyList())
                totals[name] = genre.copy(
                    songCount = genre.songCount + row.songs,
                    duration = genre.duration + row.duration.toInt(),
                    mediaProviders = (genre.mediaProviders + row.mediaProvider).distinct()
                )
            }
        }
        return totals
    }

    /** The rows' album group keys, in order, each once: rows that differ only in articles or punctuation share a key. */
    private fun List<AlbumTaggingRow>.albumKeys(): List<AlbumGroupKey> = filter { it.album != null }
        .map { albumGroupKeyOf(it.album, it.albumArtist, it.artists) }
        .distinct()

    private companion object {
        // SQLite before 3.32 (below API 31) binds at most 999 variables a statement
        const val MAX_BOUND_VARIABLES = 999
    }
}
