package com.simplecityapps.imageloading.coil

import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.ArtistHeroArtwork

/**
 * An artist hero's sources in order: the artist's [ownSources], then the [onlineSource] where the hero allows its
 * lookup, each at every one of [ArtistHeroArtwork.ARTIST_IMAGE_MINIMUM_SIZES]; then the fallback album's [albumSources].
 */
internal fun artistHeroSources(
    ownSources: List<ArtworkSource<AlbumArtist>>,
    onlineSource: ArtworkSource<AlbumArtist>,
    albumSources: List<ArtworkSource<Album>>,
): List<ArtworkSource<ArtistHeroArtwork>> = buildList {
    ArtistHeroArtwork.ARTIST_IMAGE_MINIMUM_SIZES.forEach { size ->
        ownSources.forEach { add(it.on(size) { hero: ArtistHeroArtwork -> hero.artist }) }
        add(onlineSource.on(size) { hero: ArtistHeroArtwork -> hero.artist.takeIf { hero.onlineLookup } })
    }
    albumSources.forEach { add(it.on { hero: ArtistHeroArtwork -> hero.fallbackAlbum }) }
}
