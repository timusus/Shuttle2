package com.simplecityapps.localmediaprovider.local.repository

import com.simplecityapps.localmediaprovider.local.data.room.dao.SuggestionsDao
import com.simplecityapps.localmediaprovider.local.data.room.dao.toSong
import com.simplecityapps.mediaprovider.repository.suggestions.SuggestionsRepository
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.AlbumArtistGroupKey
import com.simplecityapps.shuttle.model.AlbumGroupKey
import com.simplecityapps.shuttle.model.AlbumIdentity
import com.simplecityapps.shuttle.model.AlbumIndex
import com.simplecityapps.shuttle.model.AlbumIndexProvider
import com.simplecityapps.shuttle.model.Genre
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.model.withAlbumIdentities
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * [SuggestionsRepository] over the songs table. Albums and album artists are the library's album identities (#637),
 * resolved from the identity columns rather than whole songs, so a key found here is one the album and artist
 * repositories have; a lookup then reads only the songs of the few albums or artists it names.
 */
class LocalSuggestionsRepository(
    private val suggestionsDao: SuggestionsDao,
    private val albumIndex: AlbumIndexProvider
) : SuggestionsRepository {
    override fun songCount(): Flow<Int> = suggestionsDao.songCount()

    override suspend fun albums(keys: List<AlbumGroupKey>): List<Album> {
        val wanted = keys.filter { it.key != null }.toSet()
        if (wanted.isEmpty()) return emptyList()
        val albums = songsOf { index -> wanted.flatMap(index::songIds) }
            .groupBy { it.albumGroupKey }
            .mapValues { (key, songs) -> songs.toAlbum(key) }
        return keys.mapNotNull { albums[it] }.distinct()
    }

    override suspend fun albumArtists(keys: List<AlbumArtistGroupKey>): List<AlbumArtist> {
        val wanted = keys.toSet()
        if (wanted.isEmpty()) return emptyList()
        val albumArtists = songsOf { index -> wanted.flatMap(index::songIds) }
            .groupBy { it.albumArtistGroupKey }
            .mapValues { (key, songs) -> songs.toAlbumArtist(key) }
        return keys.mapNotNull { albumArtists[it] }.distinct()
    }

    /** Each genre's songs, summed over the genre taggings: a song tagged with several genres counts for each. */
    override suspend fun genres(): List<Genre> {
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
        return totals.values.toList()
    }

    override suspend fun recentlyCompletedAlbums(limit: Int): List<AlbumGroupKey> = latestAlbums(suggestionsDao.completedSongs().map { it.id to it.at }, limit)

    override suspend fun recentlyAddedAlbums(limit: Int): List<AlbumGroupKey> = latestAlbums(suggestionsDao.songsAdded().map { it.id to it.at }, limit)

    override suspend fun albumsToRediscover(
        minPlays: Int,
        playedBefore: Instant,
        limit: Int
    ): List<AlbumGroupKey> {
        val identities = identities()
        return suggestionsDao.playedSongs()
            .groupBy { row -> identities[row.id]?.groupKey?.takeIf { it.key != null } }
            .filterKeys { it != null }
            .filterValues { rows ->
                val plays = rows.sumOf { it.playCount }
                val lastPlayed = rows.mapNotNull { it.lastPlayed }.maxOrNull()
                (plays >= minPlays || rows.any { it.favouritedAt != null }) && (lastPlayed == null || lastPlayed < playedBefore)
            }
            .entries
            .sortedByDescending { (_, rows) -> rows.sumOf { it.playCount } }
            .take(limit)
            .mapNotNull { it.key }
    }

    private suspend fun identities(): Map<Long, AlbumIdentity> = albumIndex.albumIndex().identities

    /** The songs (not excluded) with the ids [ids] finds in the library's index, read by id, each holding its identity. */
    private suspend fun songsOf(ids: (AlbumIndex) -> List<Long>): List<Song> {
        val index = albumIndex.albumIndex()
        val identities = index.identities
        return ids(index).distinct().chunked(MAX_BOUND_VARIABLES)
            .flatMap { chunk -> suggestionsDao.songsWithIds(chunk) }
            .map { it.toSong() }
            .withAlbumIdentities(identities)
    }

    /** The albums of these (song id, time) rows, latest first by their latest song, at most [limit]; songs without an album name left out. */
    private suspend fun latestAlbums(
        rows: List<Pair<Long, Instant>>,
        limit: Int
    ): List<AlbumGroupKey> {
        val identities = identities()
        return rows
            .mapNotNull { (id, at) -> identities[id]?.groupKey?.takeIf { it.key != null }?.let { it to at } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, times) -> times.max() }
            .entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key }
    }

    private companion object {
        // SQLite before 3.32 (below API 31) binds at most 999 variables a statement
        const val MAX_BOUND_VARIABLES = 999
    }
}
