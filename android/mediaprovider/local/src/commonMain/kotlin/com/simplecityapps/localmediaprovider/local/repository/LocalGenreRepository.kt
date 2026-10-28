package com.simplecityapps.localmediaprovider.local.repository

import com.simplecityapps.localmediaprovider.local.data.room.dao.SongDataDao
import com.simplecityapps.localmediaprovider.local.data.room.dao.SongGenreRow
import com.simplecityapps.mediaprovider.repository.genres.GenreQuery
import com.simplecityapps.mediaprovider.repository.genres.GenreRepository
import com.simplecityapps.mediaprovider.repository.genres.comparator
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.mediaprovider.repository.songs.comparator
import com.simplecityapps.shuttle.model.Genre
import com.simplecityapps.shuttle.model.MinTrackLength
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.model.albumArtworkSong
import com.simplecityapps.shuttle.model.withAlbumIdentities
import com.simplecityapps.shuttle.query.SongQuery
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class LocalGenreRepository(
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val songDataDao: SongDataDao,
    val songRepository: SongRepository,
    private val albumIndex: LibraryAlbumIndex,
    private val minTrackLength: Flow<MinTrackLength> = flowOf(MinTrackLength.Off)
) : GenreRepository {
    /**
     * The ids and totals of each genre, from narrow rows rather than whole songs; the songs themselves are read only
     * for the genres asked about. Equal while the genres are, so a play count changing doesn't wake collectors.
     */
    private data class GenreSongs(
        val songIds: Map<String, List<Long>>,
        val totals: List<Genre>
    )

    private class CoverCandidate(val song: Song, val albumArtist: String, val album: String?)

    private val genreRelay: StateFlow<GenreSongs?> by lazy {
        minTrackLength
            .flatMapLatest { min -> songDataDao.genreRows(min.seconds * 1000) }
            .map { rows -> genreSongs(rows) }
            .flowOn(ioDispatcher)
            .stateIn(scope, SharingStarted.Lazily, null)
    }

    private fun genreSongs(rows: List<SongGenreRow>): GenreSongs {
        val byGenre = LinkedHashMap<String, MutableList<SongGenreRow>>()
        for (row in rows) {
            for (genre in row.genres.distinct()) {
                byGenre.getOrPut(genre) { ArrayList() }.add(row)
            }
        }
        return GenreSongs(
            songIds = byGenre.mapValues { (_, genreRows) -> genreRows.map { it.id } },
            totals = byGenre.map { (name, genreRows) ->
                Genre(name, genreRows.size, genreRows.sumOf { it.duration }, genreRows.map { it.mediaProvider }.distinct())
            }
        )
    }

    override fun getGenres(query: GenreQuery): Flow<List<Genre>> = genreRelay
        .filterNotNull()
        .map { genres ->
            genres.totals
                .filter(query.predicate)
                .sortedWith(query.sortOrder.comparator)
        }

    override fun getSongsForGenres(
        genres: List<String>,
        songQuery: SongQuery
    ): Flow<List<Song>> = genreRelay
        .filterNotNull()
        .map { relay -> genres.flatMap { genre -> relay.songIds[genre].orEmpty() } }
        .distinctUntilChanged()
        .flatMapLatest { ids ->
            // A song in several of the genres is listed once per genre, as the genres' lists are.
            songRepository.getSongs(SongQuery.SongIds(ids)).filterNotNull().map { songs ->
                val byId = songs.associateBy { it.id }
                ids.mapNotNull { id -> byId[id] }
            }
        }
        .map { songs ->
            var result = songs

            if (!songQuery.includeExcluded) {
                result = songs.filterNot { it.blacklisted }
            }

            result
                .filter(songQuery.predicate)
                .sortedWith(songQuery.sortOrder.comparator)
        }

    /**
     * One song per album identity of the genre, by album artist then album, up to [limit]: the genre's songs are the
     * genre list's own (so a song too short for the library isn't a cover) and the albums the library's [albumIndex].
     */
    override fun getGenreCoverSongs(genre: String, limit: Int): Flow<List<Song>> = genreRelay
        .filterNotNull()
        .map { relay -> relay.songIds[genre].orEmpty() }
        .distinctUntilChanged()
        .flatMapLatest { ids -> combine(songRepository.getSongs(SongQuery.SongIds(ids)).filterNotNull(), albumIndex.updates, ::Pair) }
        .map { (genreSongs, index) ->
            genreSongs
                .map { song -> CoverCandidate(song, index.identities[song.id]?.albumArtistName?.lowercase().orEmpty(), song.album?.lowercase()) }
                .sortedWith(compareBy<CoverCandidate>({ it.albumArtist }, { it.album }, { it.song.id }))
                .map { it.song }
                .groupBy { song -> index.identities[song.id]?.groupKey ?: song.id }
                .values
                .mapNotNull { albumSongs -> albumSongs.albumArtworkSong() }
                // Albums with art lead so a tile isn't a placeholder while another album of the genre has a cover; the sort is stable.
                .sortedBy { song -> song.artworkVersion == null }
                .take(limit)
                .withAlbumIdentities(index.identities)
        }
        .flowOn(ioDispatcher)
}
