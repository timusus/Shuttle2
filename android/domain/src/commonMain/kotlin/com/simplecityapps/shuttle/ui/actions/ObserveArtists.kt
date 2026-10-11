package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.mediaprovider.repository.artists.AlbumArtistQuery
import com.simplecityapps.mediaprovider.repository.artists.AlbumArtistRepository
import com.simplecityapps.shuttle.model.AlbumArtist
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow

/**
 * The artists matching [AlbumArtistQuery], by default the Artists list (every album artist, [AlbumArtistQuery.All]);
 * re-emits as they change. With Offline mode on, artists with no song that can play are left out.
 */
@Inject
class ObserveArtists(
    private val albumArtistRepository: AlbumArtistRepository,
    private val filterOfflineGroups: FilterOfflineGroups,
) {
    operator fun invoke(query: AlbumArtistQuery = AlbumArtistQuery.All()): Flow<List<AlbumArtist>> = filterOfflineGroups.albumArtists(albumArtistRepository.getAlbumArtists(query))
}
