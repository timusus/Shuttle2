package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.ArtistHeroArtwork
import com.simplecityapps.shuttle.query.SongQuery
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.first

/** [artist]'s image by the shared rule, for rows that hold only the artist, so a row matches their page's hero. */
@Inject
class LoadArtistArtwork(
    private val observeArtistAlbums: ObserveArtistAlbums,
    private val songRepository: SongRepository,
) {
    suspend operator fun invoke(artist: AlbumArtist): ArtistHeroArtwork {
        val albums = observeArtistAlbums(artist.groupKey, settled = true).first()
        val songs = songRepository.loadSongs(SongQuery.ArtistGroupKey(artist.groupKey))
        return ArtistHeroArtwork.of(artist, albums.albums, songs, albums.appearsOn)
    }
}
