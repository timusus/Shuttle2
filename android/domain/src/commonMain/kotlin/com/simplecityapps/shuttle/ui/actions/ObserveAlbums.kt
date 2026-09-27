package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.mediaprovider.repository.albums.AlbumQuery
import com.simplecityapps.mediaprovider.repository.albums.AlbumRepository
import com.simplecityapps.shuttle.model.Album
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow

/** The albums matching [AlbumQuery], the whole library by default; re-emits as they change. */
@Inject
class ObserveAlbums(
    private val albumRepository: AlbumRepository,
) {
    operator fun invoke(query: AlbumQuery = AlbumQuery.All()): Flow<List<Album>> = albumRepository.getAlbums(query)
}
