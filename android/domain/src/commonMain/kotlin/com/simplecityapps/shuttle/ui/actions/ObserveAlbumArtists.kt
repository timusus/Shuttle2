package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.mediaprovider.repository.artists.AlbumArtistQuery
import com.simplecityapps.mediaprovider.repository.artists.AlbumArtistRepository
import com.simplecityapps.shuttle.model.AlbumArtist
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow

/** The album artists matching [AlbumArtistQuery], the whole library by default; re-emits as they change. */
@Inject
class ObserveAlbumArtists(
    private val albumArtistRepository: AlbumArtistRepository,
) {
    operator fun invoke(query: AlbumArtistQuery = AlbumArtistQuery.All()): Flow<List<AlbumArtist>> = albumArtistRepository.getAlbumArtists(query)
}
