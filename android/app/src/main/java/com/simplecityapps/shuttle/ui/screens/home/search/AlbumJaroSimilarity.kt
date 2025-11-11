package com.simplecityapps.shuttle.ui.screens.home.search

import com.simplecityapps.mediaprovider.StringComparison
import com.simplecityapps.shuttle.model.Album
import kotlin.math.max

data class AlbumJaroSimilarity(
    val album: com.simplecityapps.shuttle.model.Album,
    val query: String
) {
    val nameJaroSimilarity = album.name?.let { name -> StringComparison.jaroWinklerMultiDistance(query, name) } ?: StringComparison.JaroSimilarity(0.0, emptyMap(), emptyMap())
    val albumArtistNameJaroSimilarity = album.albumArtist?.let { albumArtist -> StringComparison.jaroWinklerMultiDistance(query, albumArtist) } ?: StringComparison.JaroSimilarity(0.0, emptyMap(), emptyMap())
    val artistNameJaroSimilarity = album.artists.joinToString(" ").ifEmpty { null }?.let { name -> StringComparison.jaroWinklerMultiDistance(query, name) } ?: StringComparison.JaroSimilarity(0.0, emptyMap(), emptyMap())

    /**
     * Composite score that weighs different fields based on their importance.
     * Album name is most important (weight 1.0), followed by artist fields (0.80).
     * Exact matches get a small boost.
     */
    val compositeScore: Double by lazy {
        val nameScore = nameJaroSimilarity.score * 1.0
        val artistScore = max(artistNameJaroSimilarity.score, albumArtistNameJaroSimilarity.score) * 0.80

        val bestScore = maxOf(nameScore, artistScore)

        // Boost exact matches (score >= 0.999) by 0.01 to ensure they rank highest
        if (bestScore >= 0.999) bestScore + 0.01 else bestScore
    }
}
