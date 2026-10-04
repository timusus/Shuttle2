package com.simplecityapps.shuttle.ui.theme

import com.simplecityapps.shuttle.model.ArtistHeroArtwork
import com.simplecityapps.shuttle.model.Song

/** Loads the seed colour of the artwork a surface shows, for a scheme tinted by it. */
interface ArtworkSeedSource {
    /** The seed of [song]'s artwork: the player's, an album page's. */
    suspend fun seedFor(song: Song): ArtworkSeed

    /** The seed of the image an artist page's [hero] shows (#735, #781): the first of its candidates that loads. */
    suspend fun seedFor(hero: ArtistHeroArtwork): ArtworkSeed
}
