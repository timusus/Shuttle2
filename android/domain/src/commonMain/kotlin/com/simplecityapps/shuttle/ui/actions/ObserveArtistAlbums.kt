package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.mediaprovider.repository.albums.AlbumQuery
import com.simplecityapps.mediaprovider.repository.albums.AlbumRepository
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtistGroupKey
import com.simplecityapps.shuttle.model.isAlbumArtist
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.sorting.ArtistSongComparator
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/** An artist's [albums] and the others' albums they appear on ([appearsOn]), each newest first. */
data class ArtistAlbums(
    val albums: List<Album>,
    val appearsOn: List<Album>,
)

/** Appears On starts empty, so the artist's own albums show without waiting on its slower queries. */
@Inject
class ObserveArtistAlbums(
    private val albumRepository: AlbumRepository,
    private val songRepository: SongRepository,
) {
    /** [settled] waits for Appears On before the first emission, for a caller that takes only one. */
    operator fun invoke(
        key: AlbumArtistGroupKey,
        settled: Boolean = false,
    ): Flow<ArtistAlbums> = combine(
        albumRepository.getAlbums(AlbumQuery.ArtistGroupKey(key)),
        if (settled) appearsOn(key) else appearsOn(key).onStart { emit(emptyList()) },
    ) { albums, appearsOn ->
        ArtistAlbums(albums = albums.sortedWith(ArtistSongComparator.albumNewest), appearsOn = appearsOn.sortedWith(ArtistSongComparator.albumNewest))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun appearsOn(key: AlbumArtistGroupKey): Flow<List<Album>> = songRepository.getSongs(SongQuery.ArtistGroupKey(key))
        .filterNotNull()
        .map { songs -> songs.filter { song -> !song.isAlbumArtist(key) && !song.album.isNullOrBlank() }.mapTo(HashSet()) { song -> song.albumGroupKey } }
        .distinctUntilChanged()
        .flatMapLatest { albums ->
            if (albums.isEmpty()) flowOf(emptyList()) else albumRepository.getAlbums(AlbumQuery.AlbumGroupKeys(albums.map { AlbumQuery.AlbumGroupKey(it) }))
        }
}
