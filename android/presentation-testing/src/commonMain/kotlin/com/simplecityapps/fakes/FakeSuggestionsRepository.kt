package com.simplecityapps.fakes

import com.simplecityapps.mediaprovider.repository.suggestions.ImportDays
import com.simplecityapps.mediaprovider.repository.suggestions.SuggestionsRepository
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.AlbumArtistGroupKey
import com.simplecityapps.shuttle.model.AlbumGroupKey
import com.simplecityapps.shuttle.model.Genre
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A [SuggestionsRepository] over lists a test sets: lookups find [albums], [albumArtists] and [genres] by key, and each
 * aggregate returns its list as set, trimmed to the limit asked for.
 */
class FakeSuggestionsRepository : SuggestionsRepository {
    val songCount = MutableStateFlow(0)
    var albums: List<Album> = emptyList()
    var albumArtists: List<AlbumArtist> = emptyList()
    var genres: List<Genre> = emptyList()
    var recentlyCompleted: List<AlbumGroupKey> = emptyList()
    var recentlyAdded: List<AlbumGroupKey> = emptyList()
    var toRediscover: List<AlbumGroupKey> = emptyList()
    var importDays = ImportDays(songs = 0, largestDay = 0)

    /** Every lookup and aggregate asked for, by name, so a test can check none reads more than it needs. */
    val calls = mutableListOf<String>()

    override fun songCount(): Flow<Int> = songCount

    override suspend fun albums(keys: List<AlbumGroupKey>): List<Album> = keys.mapNotNull { key -> albums.firstOrNull { it.groupKey == key } }.distinct()

    override suspend fun albumArtists(keys: List<AlbumArtistGroupKey>): List<AlbumArtist> = keys.mapNotNull { key -> albumArtists.firstOrNull { it.groupKey == key } }.distinct()

    override suspend fun genres(names: List<String>): List<Genre> = names.distinct().mapNotNull { name -> genres.firstOrNull { it.name == name } }

    override suspend fun largestGenres(
        minSongs: Int,
        limit: Int
    ): List<Genre> = genres.filter { it.songCount >= minSongs }.sortedByDescending { it.songCount }.take(limit)

    override suspend fun recentlyCompletedAlbums(limit: Int): List<AlbumGroupKey> = recentlyCompleted.take(limit)

    override suspend fun recentlyAddedAlbums(
        since: Instant,
        limit: Int
    ): List<AlbumGroupKey> = recentlyAdded.take(limit).also { calls += "recentlyAddedAlbums($since)" }

    override suspend fun albumsToRediscover(
        minPlays: Int,
        playedBefore: Instant,
        limit: Int
    ): List<AlbumGroupKey> = toRediscover.take(limit).also { calls += "albumsToRediscover($minPlays, $playedBefore)" }

    override suspend fun importDays(): ImportDays = importDays
}
