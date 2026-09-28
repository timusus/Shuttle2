package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.mediaprovider.repository.artists.AlbumArtistQuery
import com.simplecityapps.mediaprovider.repository.artists.AlbumArtistRepository
import com.simplecityapps.shuttle.model.AlbumArtist
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow

/**
 * The artists matching [AlbumArtistQuery], by default the Artists list (every album artist, [AlbumArtistQuery.All]);
 * re-emits as they change.
 */
@Inject
class ObserveArtists(
    private val albumArtistRepository: AlbumArtistRepository,
) {
    operator fun invoke(query: AlbumArtistQuery = AlbumArtistQuery.All()): Flow<List<AlbumArtist>> = albumArtistRepository.getAlbumArtists(query)
}
