package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.mediaprovider.repository.albums.AlbumQuery
import com.simplecityapps.mediaprovider.repository.albums.AlbumRepository
import com.simplecityapps.shuttle.model.Album
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow

/** The albums matching [AlbumQuery], the whole library by default; re-emits as they change. With Offline mode on, albums with no song that can play are left out. */
@Inject
class ObserveAlbums(
    private val albumRepository: AlbumRepository,
    private val filterOfflineGroups: FilterOfflineGroups,
) {
    operator fun invoke(query: AlbumQuery = AlbumQuery.All()): Flow<List<Album>> = filterOfflineGroups.albums(albumRepository.getAlbums(query))
}
