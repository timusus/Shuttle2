package com.simplecityapps.shuttle.ui.screens.home.search

import com.simplecityapps.mediaprovider.StringComparison
import com.simplecityapps.shuttle.model.AlbumArtist
import kotlin.math.max

data class ArtistJaroSimilarity(
    val albumArtist: com.simplecityapps.shuttle.model.AlbumArtist,
    val query: String
) {
    val albumArtistNameJaroSimilarity = albumArtist.name?.let { name -> StringComparison.jaroWinklerMultiDistance(query, name) } ?: StringComparison.JaroSimilarity(0.0, emptyMap(), emptyMap())
    val artistNameJaroSimilarity = albumArtist.artists.joinToString(" ").ifEmpty { null }?.let { name -> StringComparison.jaroWinklerMultiDistance(query, name) } ?: StringComparison.JaroSimilarity(0.0, emptyMap(), emptyMap())

    /**
     * Composite score that weighs different fields based on their importance.
     * Both artist name fields are considered equally important (weight 1.0 and 0.95).
     * Exact matches get a small boost.
     */
    val compositeScore: Double by lazy {
        val albumArtistScore = albumArtistNameJaroSimilarity.score * 1.0
        val artistScore = artistNameJaroSimilarity.score * 0.95

        val bestScore = max(albumArtistScore, artistScore)

        // Boost exact matches (score >= 0.999) by 0.01 to ensure they rank highest
        if (bestScore >= 0.999) bestScore + 0.01 else bestScore
    }
}
