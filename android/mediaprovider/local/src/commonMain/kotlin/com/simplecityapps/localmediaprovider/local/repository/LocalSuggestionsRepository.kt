package com.simplecityapps.localmediaprovider.local.repository

import com.simplecityapps.localmediaprovider.local.data.room.dao.SongPlaysRow
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

    override suspend fun recentlyCompletedAlbums(limit: Int): List<AlbumGroupKey> = latestAlbums(limit, suggestionsDao::completedSongIds)

    override suspend fun recentlyAddedAlbums(limit: Int): List<AlbumGroupKey> = latestAlbums(limit, suggestionsDao::addedSongIds)

    override suspend fun albumsToRediscover(
        minPlays: Int,
        playedBefore: Instant,
        limit: Int
    ): List<AlbumGroupKey> {
        val identities = identities()
        // Album identity is resolved across songs (names, folders, ids), so it can't be a SQL column; the aggregation is
        // one pass over the played songs' narrow rows (the DAO's filter), tallying each album as its rows go by.
        val tallies = LinkedHashMap<AlbumGroupKey, RediscoverTally>()
        for (row in suggestionsDao.playedSongs()) {
            val key = identities[row.id]?.groupKey?.takeIf { it.key != null } ?: continue
            tallies.getOrPut(key) { RediscoverTally() }.add(row)
        }
        return tallies.entries
            .filter { (_, tally) -> tally.qualifies(minPlays, playedBefore) }
            .sortedByDescending { (_, tally) -> tally.plays }
            .take(limit)
            .map { it.key }
    }

    private class RediscoverTally {
        var plays = 0
        private var lastPlayed: Instant? = null
        private var favourited = false

        fun add(row: SongPlaysRow) {
            plays += row.playCount
            row.lastPlayed?.let { if (lastPlayed.let { last -> last == null || it > last }) lastPlayed = it }
            if (row.favouritedAt != null) favourited = true
        }

        fun qualifies(
            minPlays: Int,
            playedBefore: Instant
        ): Boolean = (plays >= minPlays || favourited) && lastPlayed.let { it == null || it < playedBefore }
    }

    private suspend fun identities(): Map<Long, AlbumIdentity> = albumIndex.albumIndex().identities

    /**
     * The albums of the songs [page] reads, newest first, in the order their newest song comes, at most [limit]; songs
     * without an album name left out. Pages are read until [limit] albums are found or the songs run out, so a library
     * imported at once costs a page or two, not every song.
     */
    private suspend fun latestAlbums(
        limit: Int,
        page: suspend (limit: Int, offset: Int) -> List<Long>
    ): List<AlbumGroupKey> {
        val identities = identities()
        val albums = LinkedHashSet<AlbumGroupKey>()
        var offset = 0
        while (albums.size < limit) {
            val ids = page(PAGE_SIZE, offset)
            for (id in ids) {
                identities[id]?.groupKey?.takeIf { it.key != null }?.let { albums += it }
                if (albums.size == limit) break
            }
            if (ids.size < PAGE_SIZE) break
            offset += PAGE_SIZE
        }
        return albums.toList()
    }

    private companion object {
        /** Songs read per page of a newest-first list. */
        const val PAGE_SIZE = 500
    }
}
