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
     * then album name (0.75). Exact matches get a small boost before weighting.
     */
    val compositeScore: Double by lazy {
        // Apply boost to exact matches before weighting
        val nameScoreRaw = if (nameJaroSimilarity.score >= 0.999) nameJaroSimilarity.score + 0.01 else nameJaroSimilarity.score
        val artistScoreRaw = max(artistNameJaroSimilarity.score, albumArtistNameJaroSimilarity.score)
        val artistScoreWithBoost = if (artistScoreRaw >= 0.999) artistScoreRaw + 0.01 else artistScoreRaw
        val albumScoreRaw = if (albumNameJaroSimilarity.score >= 0.999) albumNameJaroSimilarity.score + 0.01 else albumNameJaroSimilarity.score

        // Apply weighting after boost
        val nameScore = nameScoreRaw * 1.0
        val artistScore = artistScoreWithBoost * 0.85
        val albumScore = albumScoreRaw * 0.75

        maxOf(nameScore, artistScore, albumScore)
    }

    /**
     * Length of the song name after stripping articles, used for tie-breaking.
     * When multiple songs have the same score, prefer shorter names.
     */
    val strippedNameLength: Int by lazy {
        stripArticlesForSorting(song.name ?: "").length
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
