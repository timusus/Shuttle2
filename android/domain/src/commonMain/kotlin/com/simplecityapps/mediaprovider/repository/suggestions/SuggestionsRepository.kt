package com.simplecityapps.mediaprovider.repository.suggestions

import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.AlbumArtistGroupKey
import com.simplecityapps.shuttle.model.AlbumGroupKey
import com.simplecityapps.shuttle.model.Genre
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * The library as Home's suggestions read it (#633): aggregates over the songs with a limit, and lookups of the few
 * albums, artists and genres a section shows, so nothing here loads every song whole. Excluded songs are left out
 * throughout. Albums and artists are grouped by [com.simplecityapps.shuttle.model.AlbumIdentityRule], as the album and
 * artist repositories group them.
 */
interface SuggestionsRepository {
    /** How many songs the library holds, re-emitted whenever the songs change. */
    fun songCount(): Flow<Int>

    /** The albums with [keys], in the order of [keys]; a key no album has is left out. */
    suspend fun albums(keys: List<AlbumGroupKey>): List<Album>

    /** The album artists with [keys], in the order of [keys]; a key no artist has is left out. */
    suspend fun albumArtists(keys: List<AlbumArtistGroupKey>): List<AlbumArtist>

    /** The genres named [names], in the order of [names]; a name no song carries is left out. */
    suspend fun genres(names: List<String>): List<Genre>

    /** The genres with at least [minSongs] songs, largest first; at most [limit]. */
    suspend fun largestGenres(
        minSongs: Int,
        limit: Int
    ): List<Genre>

    /** The albums one of whose songs has played through, most recently first; at most [limit]. */
    suspend fun recentlyCompletedAlbums(limit: Int): List<AlbumGroupKey>

    /** The albums with a song added since [since], by their newest song, most recent first; at most [limit]. */
    suspend fun recentlyAddedAlbums(
        since: Instant,
        limit: Int
    ): List<AlbumGroupKey>

    /**
     * The albums played through at least [minPlays] times all together or holding a favourite, none of whose songs has
     * played since [playedBefore]; most played first, at most [limit].
     */
    suspend fun albumsToRediscover(
        minPlays: Int,
        playedBefore: Instant,
        limit: Int
    ): List<AlbumGroupKey>

    /** How many songs the library holds, and how many of them the single busiest day added. */
    suspend fun importDays(): ImportDays
}

/** The library's [songs], [largestDay] of which were added on the same (UTC) day. */
data class ImportDays(
    val songs: Int,
    val largestDay: Int
)
