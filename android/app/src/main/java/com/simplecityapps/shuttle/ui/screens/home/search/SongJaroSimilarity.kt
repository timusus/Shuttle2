package com.simplecityapps.shuttle.ui.screens.home.search

import com.simplecityapps.mediaprovider.StringComparison
import com.simplecityapps.shuttle.model.Song
import kotlin.math.max

data class SongJaroSimilarity(
    val song: com.simplecityapps.shuttle.model.Song,
    val query: String
) {
    val nameJaroSimilarity = song.name?.let { StringComparison.jaroWinklerMultiDistance(query, it) } ?: StringComparison.JaroSimilarity(0.0, emptyMap(), emptyMap())
    val albumNameJaroSimilarity = song.album?.let { StringComparison.jaroWinklerMultiDistance(query, it) } ?: StringComparison.JaroSimilarity(0.0, emptyMap(), emptyMap())
    val albumArtistNameJaroSimilarity = song.albumArtist?.let { StringComparison.jaroWinklerMultiDistance(query, it) } ?: StringComparison.JaroSimilarity(0.0, emptyMap(), emptyMap())
    val artistNameJaroSimilarity = song.friendlyArtistName?.let { name -> StringComparison.jaroWinklerMultiDistance(query, name) } ?: StringComparison.JaroSimilarity(0.0, emptyMap(), emptyMap())

    /**
     * Composite score that weighs different fields based on their importance.
     * Song name is most important (weight 1.0), followed by artist fields (0.85),
     * then album name (0.75). Exact matches get a small boost.
     */
    val compositeScore: Double by lazy {
        val nameScore = nameJaroSimilarity.score * 1.0
        val artistScore = max(artistNameJaroSimilarity.score, albumArtistNameJaroSimilarity.score) * 0.85
        val albumScore = albumNameJaroSimilarity.score * 0.75

        val bestScore = maxOf(nameScore, artistScore, albumScore)

        // Boost exact matches (score >= 0.999) by 0.01 to ensure they rank highest
        if (bestScore >= 0.999) bestScore + 0.01 else bestScore
    }
}
