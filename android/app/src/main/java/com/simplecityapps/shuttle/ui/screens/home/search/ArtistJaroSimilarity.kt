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
     * Exact matches get a small boost before weighting.
     */
    val compositeScore: Double by lazy {
        // Apply boost to exact matches before weighting
        val albumArtistScoreRaw = if (albumArtistNameJaroSimilarity.score >= 0.999) albumArtistNameJaroSimilarity.score + 0.01 else albumArtistNameJaroSimilarity.score
        val artistScoreRaw = if (artistNameJaroSimilarity.score >= 0.999) artistNameJaroSimilarity.score + 0.01 else artistNameJaroSimilarity.score

        // Apply weighting after boost
        val albumArtistScore = albumArtistScoreRaw * 1.0
        val artistScore = artistScoreRaw * 0.95

        max(albumArtistScore, artistScore)
    }

    /**
     * Length of the artist name after stripping articles, used for tie-breaking.
     * When multiple artists have the same score, prefer shorter names.
     */
    val strippedNameLength: Int by lazy {
        stripArticlesForSorting(albumArtist.name ?: "").length
    }

    companion object {
        // Helper to strip articles for tie-breaking (matches StringComparison.stripArticles behavior)
        private fun stripArticlesForSorting(s: String): String {
            val normalized = s.lowercase().trim()
            val articles = listOf("the", "a", "an", "el", "la", "los", "las", "le", "les", "der", "die", "das")
            for (article in articles) {
                val pattern = "^$article\\s+"
                if (normalized.matches(Regex(pattern + ".*"))) {
                    return normalized.replaceFirst(Regex(pattern), "")
                }
            }
            return normalized
        }
    }
}
