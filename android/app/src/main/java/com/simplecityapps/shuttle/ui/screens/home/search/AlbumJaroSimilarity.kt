package com.simplecityapps.shuttle.ui.screens.home.search

import com.simplecityapps.mediaprovider.StringComparison
import com.simplecityapps.shuttle.model.Album
import kotlin.math.max

data class AlbumJaroSimilarity(
    val album: com.simplecityapps.shuttle.model.Album,
    val query: String
) {
    /**
     * Enum representing which field had the best match.
     * Used for highlighting the matched field in the UI.
     */
    enum class MatchedField {
        NAME, // Album name
        ARTIST // Artist or album artist
    }

    val nameJaroSimilarity = album.name?.let { name -> StringComparison.jaroWinklerMultiDistance(query, name) } ?: StringComparison.JaroSimilarity(0.0, emptyMap(), emptyMap())

    // Use the same string that will be displayed in the UI (albumArtist ?: friendlyArtistName)
    // This ensures matched indices align with the displayed text
    val displayArtistName = album.albumArtist ?: album.friendlyArtistName
    val albumArtistNameJaroSimilarity = displayArtistName?.let { artistName -> StringComparison.jaroWinklerMultiDistance(query, artistName) } ?: StringComparison.JaroSimilarity(0.0, emptyMap(), emptyMap())
    val artistNameJaroSimilarity = album.artists.joinToString(" ").ifEmpty { null }?.let { name -> StringComparison.jaroWinklerMultiDistance(query, name) } ?: StringComparison.JaroSimilarity(0.0, emptyMap(), emptyMap())

    /**
     * Composite score using research-backed ranking algorithm.
     *
     * Key improvements over previous implementation:
     * 1. Exact match boost is multiplicative (×2.5) instead of additive (+0.01)
     *    - Research shows 2.0-5.0× is industry standard (Elasticsearch/Solr)
     *    - Ensures exact artist "Tool" ranks above fuzzy album "Toolbox"
     *
     * 2. Increased field weight for artist
     *    - Artist: 0.85 (up from 0.80) - artist matches are important
     *
     * 3. DisMax tie-breaker scoring (optional, currently 0.0)
     *    - Rewards albums that match multiple fields
     *    - Can be tuned from 0.0 (only best field) to 0.3 (30% bonus from other fields)
     *
     * Example scores with 2.5× exact match multiplier:
     * - Album "Abbey Road" (exact name): 1.0 × 1.0 × 2.5 = 2.5
     * - Album "Road to Nowhere" (fuzzy name 0.88): 0.88 × 1.0 = 0.88
     * - Album "Lateralus" by Tool (exact artist): 1.0 × 0.85 × 2.5 = 2.125
     */
    val compositeScore: Double by lazy {
        // Exact match multiplier based on Elasticsearch/Solr research
        // Range: 2.0 (conservative) to 5.0 (aggressive), 2.5 is balanced
        val exactMatchMultiplier = 2.5

        // Tie-breaker: 0.0 = only best field, 0.3 = add 30% of other fields
        // Currently 0.0 to match existing behavior, can tune to 0.3 for multi-field bonus
        val tieBreaker = 0.0

        // Apply multiplicative boost for exact matches (research-backed approach)
        val nameScoreRaw = nameJaroSimilarity.score
        val nameScoreWithBoost = if (nameScoreRaw >= 0.999) {
            nameScoreRaw * exactMatchMultiplier
        } else {
            nameScoreRaw
        }

        val artistScoreRaw = max(artistNameJaroSimilarity.score, albumArtistNameJaroSimilarity.score)
        val artistScoreWithBoost = if (artistScoreRaw >= 0.999) {
            artistScoreRaw * exactMatchMultiplier
        } else {
            artistScoreRaw
        }

        // Apply field weights (increased artist from 0.80 to 0.85)
        val nameScore = nameScoreWithBoost * 1.0 // Primary field
        val artistScore = artistScoreWithBoost * 0.85 // Secondary (up from 0.80)

        // DisMax scoring: best match + tie-breaker bonus for other fields
        val allScores = listOf(nameScore, artistScore).sortedDescending()
        val bestScore = allScores[0]
        val otherScoresSum = allScores.drop(1).sum()

        bestScore + (tieBreaker * otherScoresSum)
    }

    /**
     * Which field had the best match (for highlighting in UI).
     */
    val matchedField: MatchedField by lazy {
        val nameScore_internal = run {
            val exactMatchMultiplier = 2.5
            val nameScoreRaw = nameJaroSimilarity.score
            val nameScoreWithBoost = if (nameScoreRaw >= 0.999) nameScoreRaw * exactMatchMultiplier else nameScoreRaw
            nameScoreWithBoost * 1.0
        }

        val artistScore_internal = run {
            val exactMatchMultiplier = 2.5
            val artistScoreRaw = max(artistNameJaroSimilarity.score, albumArtistNameJaroSimilarity.score)
            val artistScoreWithBoost = if (artistScoreRaw >= 0.999) artistScoreRaw * exactMatchMultiplier else artistScoreRaw
            artistScoreWithBoost * 0.85
        }

        if (nameScore_internal >= artistScore_internal) MatchedField.NAME else MatchedField.ARTIST
    }

    /**
     * The matched indices for the best-matched field (for highlighting).
     */
    val matchedIndices: Map<Int, Double> by lazy {
        when (matchedField) {
            MatchedField.NAME -> nameJaroSimilarity.bMatchedIndices
            MatchedField.ARTIST -> {
                val artistRaw = max(artistNameJaroSimilarity.score, albumArtistNameJaroSimilarity.score)
                if (artistRaw == artistNameJaroSimilarity.score) {
                    artistNameJaroSimilarity.bMatchedIndices
                } else {
                    albumArtistNameJaroSimilarity.bMatchedIndices
                }
            }
        }
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
