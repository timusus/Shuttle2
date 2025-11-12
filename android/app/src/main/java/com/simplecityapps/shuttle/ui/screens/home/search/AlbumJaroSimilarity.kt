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
     * Exact matches get a small boost before weighting.
     */
    val compositeScore: Double by lazy {
        // Apply boost to exact matches before weighting
        val nameScoreRaw = if (nameJaroSimilarity.score >= 0.999) nameJaroSimilarity.score + 0.01 else nameJaroSimilarity.score
        val artistScoreRaw = max(artistNameJaroSimilarity.score, albumArtistNameJaroSimilarity.score)
        val artistScoreWithBoost = if (artistScoreRaw >= 0.999) artistScoreRaw + 0.01 else artistScoreRaw

        // Apply weighting after boost
        val nameScore = nameScoreRaw * 1.0
        val artistScore = artistScoreWithBoost * 0.80

        maxOf(nameScore, artistScore)
    }

    /**
     * Length of the album name after stripping articles, used for tie-breaking.
     * When multiple albums have the same score, prefer shorter names.
     */
    val strippedNameLength: Int by lazy {
        stripArticlesForSorting(album.name ?: "").length
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
