package com.simplecityapps.shuttle.ui.theme

import com.simplecityapps.shuttle.model.Song

/** Loads the seed colour of a song's artwork, for a scheme tinted by it. */
fun interface ArtworkSeedSource {
    suspend fun seedFor(song: Song): ArtworkSeed
}
