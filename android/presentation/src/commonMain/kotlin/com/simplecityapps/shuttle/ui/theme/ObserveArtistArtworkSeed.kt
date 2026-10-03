package com.simplecityapps.shuttle.ui.theme

import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.settings.ObserveSetting
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow

/**
 * The artwork seed for an artist page, from the artist's own artwork, which its hero shows (#735). Otherwise as
 * [ObserveArtworkSeed]: None while Colour from artwork is off, extracted again only when the artwork changes.
 */
class ObserveArtistArtworkSeed @Inject constructor(
    private val seedSource: ArtworkSeedSource,
    private val observeSetting: ObserveSetting,
) {
    operator fun invoke(artist: Flow<AlbumArtist?>): Flow<ArtworkSeed> = observeSeed(artist, observeSetting, ::sameArtwork) { seedSource.seedFor(it) }

    private fun sameArtwork(old: AlbumArtist, new: AlbumArtist): Boolean = (old.name ?: old.friendlyArtistName) == (new.name ?: new.friendlyArtistName) &&
        old.artworkVersion == new.artworkVersion
}
