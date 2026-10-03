package com.simplecityapps.shuttle.ui.theme

import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.Song

/** Loads the seed colour of the artwork a surface shows, for a scheme tinted by it. */
interface ArtworkSeedSource {
    /** The seed of [song]'s artwork: the player's, an album page's. */
    suspend fun seedFor(song: Song): ArtworkSeed

    /** The seed of [artist]'s own artwork, the image an artist page's hero shows (#735). */
    suspend fun seedFor(artist: AlbumArtist): ArtworkSeed
}
