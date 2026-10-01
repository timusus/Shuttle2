package com.simplecityapps.fakes

import com.simplecityapps.mediaprovider.repository.suggestions.SuggestionsRepository
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.AlbumArtistGroupKey
import com.simplecityapps.shuttle.model.AlbumGroupKey
import com.simplecityapps.shuttle.model.Genre
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A [SuggestionsRepository] over lists a test sets: lookups find [albums], [albumArtists] and [genres] by key, and each
 * aggregate returns its list as set, trimmed to the limit asked for. Each suspending call takes [latency] first.
 */
class FakeSuggestionsRepository : SuggestionsRepository {
    val songCount = MutableStateFlow(0)
    var albums: List<Album> = emptyList()
    var albumArtists: List<AlbumArtist> = emptyList()
    var genres: List<Genre> = emptyList()
    var recentlyCompleted: List<AlbumGroupKey> = emptyList()
    var recentlyAdded: List<AlbumGroupKey> = emptyList()
    var toRediscover: List<AlbumGroupKey> = emptyList()
    var latency: Duration = Duration.ZERO

    /** Every lookup and aggregate asked for, by name, so a test can check none reads more than it needs. */
    val calls = mutableListOf<String>()

    override fun songCount(): Flow<Int> = songCount

    override suspend fun albums(keys: List<AlbumGroupKey>): List<Album> = afterLatency { keys.mapNotNull { key -> albums.firstOrNull { it.groupKey == key } }.distinct() }

    override suspend fun albumArtists(keys: List<AlbumArtistGroupKey>): List<AlbumArtist> = afterLatency { keys.mapNotNull { key -> albumArtists.firstOrNull { it.groupKey == key } }.distinct() }

    override suspend fun genres(names: List<String>): List<Genre> = afterLatency { names.distinct().mapNotNull { name -> genres.firstOrNull { it.name == name } } }

    override suspend fun largestGenres(
        minSongs: Int,
        limit: Int
    ): List<Genre> = afterLatency { genres.filter { it.songCount >= minSongs }.sortedByDescending { it.songCount }.take(limit) }

    override suspend fun recentlyCompletedAlbums(limit: Int): List<AlbumGroupKey> = afterLatency { recentlyCompleted.take(limit) }

    override suspend fun recentlyAddedAlbums(limit: Int): List<AlbumGroupKey> = afterLatency { recentlyAdded.take(limit).also { calls += "recentlyAddedAlbums($limit)" } }

    override suspend fun albumsToRediscover(
        minPlays: Int,
        playedBefore: Instant,
        limit: Int
    ): List<AlbumGroupKey> = afterLatency { toRediscover.take(limit).also { calls += "albumsToRediscover($minPlays, $playedBefore)" } }

    private suspend fun <T> afterLatency(block: () -> T): T {
        delay(latency)
        return block()
    }
}
