package com.simplecityapps.shuttle.ui.theme

import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.ArtistHeroArtwork
import com.simplecityapps.shuttle.settings.ObserveSetting
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow

/**
 * The artwork seed for an artist page, from the image its hero shows (#735, #781): the first of the [ArtistHeroArtwork]'s
 * candidates that loads. Otherwise as [ObserveArtworkSeed]: None while Colour from artwork is off, extracted again only
 * when one of those candidates changes.
 */
class ObserveArtistArtworkSeed @Inject constructor(
    private val seedSource: ArtworkSeedSource,
    private val observeSetting: ObserveSetting,
) {
    operator fun invoke(hero: Flow<ArtistHeroArtwork?>): Flow<ArtworkSeed> = observeSeed(hero, observeSetting, ::sameArtwork) { seedSource.seedFor(it) }

    private fun sameArtwork(old: ArtistHeroArtwork, new: ArtistHeroArtwork): Boolean = sameArtwork(old.artist, new.artist) &&
        old.onlineLookup == new.onlineLookup &&
        sameArtwork(old.fallbackAlbum, new.fallbackAlbum)

    private fun sameArtwork(old: AlbumArtist, new: AlbumArtist): Boolean = (old.name ?: old.friendlyArtistName) == (new.name ?: new.friendlyArtistName) &&
        old.artworkVersion == new.artworkVersion

    private fun sameArtwork(old: Album?, new: Album?): Boolean = old?.groupKey == new?.groupKey && old?.artworkVersion == new?.artworkVersion
}
