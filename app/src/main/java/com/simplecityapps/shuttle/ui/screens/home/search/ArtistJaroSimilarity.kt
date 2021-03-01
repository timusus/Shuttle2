package com.simplecityapps.shuttle.ui.screens.home.search

import com.simplecityapps.mediaprovider.StringComparison
import com.simplecityapps.mediaprovider.model.AlbumArtist

data class ArtistJaroSimilarity(
    val artist: AlbumArtist,
    val query: String
) {
    val nameJaroSimilarity = StringComparison.jaroWinklerDistance(query, artist.name)
}