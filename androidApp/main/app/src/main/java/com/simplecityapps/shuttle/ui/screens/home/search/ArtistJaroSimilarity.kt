package com.simplecityapps.shuttle.ui.screens.home.search

import com.simplecityapps.mediaprovider.StringComparison
import com.simplecityapps.shuttle.model.AlbumArtist

data class ArtistJaroSimilarity(
    val albumArtist: com.simplecityapps.shuttle.model.AlbumArtist,
    val query: String
) {
    val albumArtistNameJaroSimilarity = albumArtist.name?.let { name -> StringComparison.jaroWinklerMultiDistance(query, name) } ?: StringComparison.JaroSimilarity(0.0, emptyMap(), emptyMap())
    val artistNameJaroSimilarity = albumArtist.artists.joinToString(" ").ifEmpty { null }?.let { name -> StringComparison.jaroWinklerMultiDistance(query, name) } ?: StringComparison.JaroSimilarity(0.0, emptyMap(), emptyMap())
}
