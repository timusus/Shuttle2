package com.simplecityapps.shuttle.ui.screens.home.search

import com.simplecityapps.mediaprovider.StringComparison
import com.simplecityapps.mediaprovider.model.AlbumArtist

data class ArtistJaroSimilarity(
    val artist: AlbumArtist,
    val query: String
) {
    val nameJaroSimilarity = artist.name?.let { name -> StringComparison.jaroWinklerDistance(query, name) } ?: StringComparison.JaroSimilarity(0.0, emptyMap(), emptyMap())
}