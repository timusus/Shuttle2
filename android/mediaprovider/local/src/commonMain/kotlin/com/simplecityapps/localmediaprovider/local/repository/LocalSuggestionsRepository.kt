package com.simplecityapps.localmediaprovider.local.repository

import com.simplecityapps.localmediaprovider.local.data.room.dao.SuggestionsDao
import com.simplecityapps.mediaprovider.repository.genres.GenreQuery
import com.simplecityapps.mediaprovider.repository.genres.GenreRepository
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.mediaprovider.repository.suggestions.SuggestionsRepository
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.AlbumArtistGroupKey
import com.simplecityapps.shuttle.model.AlbumGroupKey
import com.simplecityapps.shuttle.model.AlbumIdentity
import com.simplecityapps.shuttle.model.AlbumIndexProvider
import com.simplecityapps.shuttle.model.Genre
import com.simplecityapps.shuttle.query.SongQuery
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * [SuggestionsRepository] over the songs table. Albums and album artists are the library's album identities (#637),
 * resolved from the identity columns rather than whole songs; a lookup then reads only the songs of the few albums or
 * artists it names. What Home shows (the song count, genres, albums, album artists) comes from [songRepository] and
 * [genreRepository], so it holds the same songs the library does (not excluded, not under the minimum track length):
 * an album key found here whose songs are all hidden finds no album.
 */
class LocalSuggestionsRepository(
    private val suggestionsDao: SuggestionsDao,
    private val albumIndex: AlbumIndexProvider,
    private val songRepository: SongRepository,
    private val genreRepository: GenreRepository
) : SuggestionsRepository {
    override fun songCount(): Flow<Int> = songRepository.getSongs(SongQuery.All()).filterNotNull().map { it.size }.distinctUntilChanged()

    override suspend fun albums(keys: List<AlbumGroupKey>): List<Album> {
        val wanted = keys.filter { it.key != null }.distinct()
        if (wanted.isEmpty()) return emptyList()
        val albums = songRepository.loadSongs(SongQuery.AlbumGroupKeys(wanted.map { SongQuery.AlbumGroupKey(it) }))
            .groupBy { it.albumGroupKey }
            .mapValues { (key, songs) -> songs.toAlbum(key) }
        return keys.mapNotNull { albums[it] }.distinct()
    }

    override suspend fun albumArtists(keys: List<AlbumArtistGroupKey>): List<AlbumArtist> {
        val wanted = keys.distinct()
        if (wanted.isEmpty()) return emptyList()
        val albumArtists = songRepository.loadSongs(SongQuery.ArtistGroupKeys(wanted.map { SongQuery.ArtistGroupKey(it) }))
            .groupBy { it.albumArtistGroupKey }
            .mapValues { (key, songs) -> songs.toAlbumArtist(key) }
        return keys.mapNotNull { albumArtists[it] }.distinct()
    }

    /** The Genres screen's genres: a song tagged with several genres counts for each. */
    override suspend fun genres(): List<Genre> = genreRepository.getGenres(GenreQuery.All()).first()

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
}
