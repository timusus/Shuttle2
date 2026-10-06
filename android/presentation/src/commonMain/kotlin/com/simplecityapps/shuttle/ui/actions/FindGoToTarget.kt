package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.mediaprovider.repository.albums.AlbumQuery
import com.simplecityapps.mediaprovider.repository.albums.AlbumRepository
import com.simplecityapps.mediaprovider.repository.artists.AlbumArtistQuery
import com.simplecityapps.mediaprovider.repository.artists.AlbumArtistRepository
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.firstOrNull

/** Finds the album or artist screen a "Go to album" or "Go to artist" action opens, for a single song or album. */
class FindGoToTarget @Inject constructor(
    private val albumRepository: AlbumRepository,
    private val albumArtistRepository: AlbumArtistRepository,
) {
    enum class Destination { Album, AlbumArtist }

    /** Null when the selection isn't a single song (or, for [Destination.AlbumArtist], album), or it isn't in the library. */
    suspend operator fun invoke(selection: MediaSelection, destination: Destination): NavigationTarget? = when (destination) {
        Destination.Album -> {
            val song = (selection as? MediaSelection.Songs)?.songs?.singleOrNull()
            song?.let {
                albumRepository.getAlbums(AlbumQuery.AlbumGroupKey(it.albumGroupKey))
                    .firstOrNull()?.firstOrNull()
                    ?.let(NavigationTarget::Album)
            }
        }

        Destination.AlbumArtist -> {
            // The primary album artist: an album of several goes to its first, "A feat. B"'s to A
            val groupKey = when (selection) {
                is MediaSelection.Songs -> selection.songs.singleOrNull()?.albumArtistKeys?.firstOrNull()
                is MediaSelection.Albums -> selection.albums.singleOrNull()?.albumArtistKeys?.firstOrNull()
                else -> null
            }
            groupKey?.let {
                albumArtistRepository.getAlbumArtists(AlbumArtistQuery.AlbumArtistGroupKey(it))
                    .firstOrNull()?.firstOrNull()
                    ?.let(NavigationTarget::AlbumArtist)
            }
        }
    }
}
