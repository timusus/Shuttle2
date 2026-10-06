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

/** An artist's albums (#637): [albums], those they're the album artist of, and [appearsOn], others' albums crediting them. Each newest first. */
data class ArtistAlbums(
    val albums: List<Album>,
    val appearsOn: List<Album>,
)

/**
 * The [ArtistAlbums] of the artist a key names; re-emits as they change. Appears On holds every album with a song
 * crediting them ([com.simplecityapps.shuttle.model.ArtistCredits]) whose album artist is someone else (a compilation's
 * is "Various Artists"); songs without an album name make no album to appear on.
 *
 * Appears On needs a songs query and then an albums query, so it starts empty: the artist's own albums emit as soon
 * as they load rather than waiting on it, which was what kept the artist page on its spinner (#677).
 */
@Inject
class ObserveArtistAlbums(
    private val albumRepository: AlbumRepository,
    private val songRepository: SongRepository,
) {
    /**
     * [settled] makes the first emission wait for Appears On, for a caller that takes just the one ([LoadArtistArtwork]),
     * rather than starting with it empty.
     */
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
