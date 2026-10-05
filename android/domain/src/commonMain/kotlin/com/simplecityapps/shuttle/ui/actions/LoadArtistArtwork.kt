package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.ArtistHeroArtwork
import com.simplecityapps.shuttle.query.SongQuery
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.first

/**
 * [artist]'s image by the shared rule ([ArtistHeroArtwork]), for a surface that holds only the artist: their rows in
 * lists and search (#823), so a row shows what their page's hero does. The page, which observes their albums and songs
 * already, builds the same with [ArtistHeroArtwork.of].
 */
@Inject
class LoadArtistArtwork(
    private val observeArtistAlbums: ObserveArtistAlbums,
    private val songRepository: SongRepository,
) {
    suspend operator fun invoke(artist: AlbumArtist): ArtistHeroArtwork {
        val albums = observeArtistAlbums.settled(artist.groupKey).first()
        val songs = songRepository.loadSongs(SongQuery.ArtistGroupKey(artist.groupKey))
        return ArtistHeroArtwork.of(artist, albums.albums, songs, albums.appearsOn)
    }
}
