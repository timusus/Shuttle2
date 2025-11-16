package com.simplecityapps.shuttle.ui.screens.home.search

import com.simplecityapps.mediaprovider.StringComparison
import com.simplecityapps.shuttle.model.AlbumArtist
import kotlin.math.max

data class ArtistJaroSimilarity(
    val albumArtist: com.simplecityapps.shuttle.model.AlbumArtist,
    val query: String
) {
    /**
     * Enum representing which artist field had the best match.
     * Used for highlighting the matched field in the UI.
     */
    enum class MatchedField {
        ALBUM_ARTIST,  // Album artist name
        ARTIST         // Joined artist names
    }

    // Use the same string that will be displayed in the UI (name ?: friendlyArtistName)
    // This ensures matched indices align with the displayed text
    val displayName = albumArtist.name ?: albumArtist.friendlyArtistName
    val albumArtistNameJaroSimilarity = displayName?.let { name -> StringComparison.jaroWinklerMultiDistance(query, name) } ?: StringComparison.JaroSimilarity(0.0, emptyMap(), emptyMap())
    val artistNameJaroSimilarity = albumArtist.artists.joinToString(" ").ifEmpty { null }?.let { name -> StringComparison.jaroWinklerMultiDistance(query, name) } ?: StringComparison.JaroSimilarity(0.0, emptyMap(), emptyMap())

    /**
     * Composite score using research-backed ranking algorithm.
     *
     * Key improvements over previous implementation:
     * 1. Exact match boost is multiplicative (×2.5) instead of additive (+0.01)
     *    - Research shows 2.0-5.0× is industry standard (Elasticsearch/Solr)
     *    - Ensures exact matches rank significantly higher
     *
     * 2. Both artist fields weighted equally high (1.0 and 0.98)
     *    - Album artist and joined artist names are both important
     *
     * 3. DisMax tie-breaker scoring (optional, currently 0.0)
     *    - Rewards artists when both fields match
     *    - Can be tuned from 0.0 (only best field) to 0.3 (30% bonus from other fields)
     *
     * Example scores with 2.5× exact match multiplier:
     * - Artist "Tool" (exact match): 1.0 × 1.0 × 2.5 = 2.5
     * - Artist "Toolbox" (fuzzy match 0.92): 0.92 × 1.0 = 0.92
     */
    val compositeScore: Double by lazy {
        // Exact match multiplier based on Elasticsearch/Solr research
        // Range: 2.0 (conservative) to 5.0 (aggressive), 2.5 is balanced
        val exactMatchMultiplier = 2.5

        // Tie-breaker: 0.0 = only best field, 0.3 = add 30% of other fields
        // Currently 0.0 to match existing behavior, can tune to 0.3 for multi-field bonus
        val tieBreaker = 0.0

        // Apply multiplicative boost for exact matches (research-backed approach)
        val albumArtistScoreRaw = albumArtistNameJaroSimilarity.score
        val albumArtistScoreWithBoost = if (albumArtistScoreRaw >= 0.999) {
            albumArtistScoreRaw * exactMatchMultiplier
        } else {
            albumArtistScoreRaw
        }

        val artistScoreRaw = artistNameJaroSimilarity.score
        val artistScoreWithBoost = if (artistScoreRaw >= 0.999) {
            artistScoreRaw * exactMatchMultiplier
        } else {
            artistScoreRaw
        }

        // Apply field weights (both fields weighted almost equally)
        val albumArtistScore = albumArtistScoreWithBoost * 1.0    // Primary field
        val artistScore = artistScoreWithBoost * 0.98             // Nearly equal (up from 0.95)

        // DisMax scoring: best match + tie-breaker bonus for other fields
        val allScores = listOf(albumArtistScore, artistScore).sortedDescending()
        val bestScore = allScores[0]
        val otherScoresSum = allScores.drop(1).sum()

        bestScore + (tieBreaker * otherScoresSum)
    }

    /**
     * Which field had the best match (for highlighting in UI).
     */
    val matchedField: MatchedField by lazy {
        val albumArtistScore_internal = run {
            val exactMatchMultiplier = 2.5
            val albumArtistScoreRaw = albumArtistNameJaroSimilarity.score
            val albumArtistScoreWithBoost = if (albumArtistScoreRaw >= 0.999) albumArtistScoreRaw * exactMatchMultiplier else albumArtistScoreRaw
            albumArtistScoreWithBoost * 1.0
        }

        val artistScore_internal = run {
            val exactMatchMultiplier = 2.5
            val artistScoreRaw = artistNameJaroSimilarity.score
            val artistScoreWithBoost = if (artistScoreRaw >= 0.999) artistScoreRaw * exactMatchMultiplier else artistScoreRaw
            artistScoreWithBoost * 0.98
        }

        if (albumArtistScore_internal >= artistScore_internal) MatchedField.ALBUM_ARTIST else MatchedField.ARTIST
    }

    /**
     * The matched indices for the best-matched field (for highlighting).
     */
    val matchedIndices: Map<Int, Double> by lazy {
        when (matchedField) {
            MatchedField.ALBUM_ARTIST -> albumArtistNameJaroSimilarity.bMatchedIndices
            MatchedField.ARTIST -> artistNameJaroSimilarity.bMatchedIndices
        }
    }

    /**
     * Length of the artist name after stripping articles, used for tie-breaking.
     * When multiple artists have the same score, prefer shorter names.
     */
    val strippedNameLength: Int by lazy {
        stripArticlesForSorting(displayName ?: "").length
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
