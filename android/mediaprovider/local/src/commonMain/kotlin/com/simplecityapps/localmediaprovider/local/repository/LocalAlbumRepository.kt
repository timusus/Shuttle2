package com.simplecityapps.localmediaprovider.local.repository

import com.simplecityapps.mediaprovider.repository.albums.AlbumQuery
import com.simplecityapps.mediaprovider.repository.albums.AlbumRepository
import com.simplecityapps.mediaprovider.repository.albums.comparator
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.MinTrackLength
import com.simplecityapps.shuttle.model.Song
import kotlin.random.Random
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class LocalAlbumRepository(
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val songs: Flow<List<Song>>,
    private val minTrackLength: Flow<MinTrackLength> = flowOf(MinTrackLength.Off)
) : AlbumRepository {
    private val albumsRelay: StateFlow<List<Album>?> by lazy {
        combine(songs.distinctUntilChanged(), minTrackLength) { songs, min -> songs.filter(min::keeps) }
            .map { songs ->
                songs
                    .groupBy { it.albumGroupKey }
                    .map { (key, songs) -> songs.toAlbum(key) }
            }
            .flowOn(ioDispatcher)
            .stateIn(scope, SharingStarted.Lazily, null)
    }

    override fun getAlbums(query: AlbumQuery): Flow<List<Album>> {
        // Random needs a seed; generate it once per getAlbums() call so the same collector sees
        // a stable order across re-emissions of albumsRelay, and reshuffles on the next call.
        val comparator = query.sortOrder.comparator(seed = Random.nextLong())
        return albumsRelay
            .filterNotNull()
            .map { albums ->
                albums
                    .filter(query.predicate)
                    .sortedWith(comparator)
            }
    }
}
