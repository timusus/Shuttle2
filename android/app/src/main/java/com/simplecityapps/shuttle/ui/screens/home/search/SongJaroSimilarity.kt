package com.simplecityapps.shuttle.ui.screens.home.search

import com.simplecityapps.mediaprovider.StringComparison
import com.simplecityapps.shuttle.model.Song
import kotlin.math.max

data class SongJaroSimilarity(
    val song: com.simplecityapps.shuttle.model.Song,
    val query: String
) {
    /**
     * Enum representing which field had the best match.
     * Used for highlighting the matched field in the UI.
     */
    enum class MatchedField {
        NAME,    // Song name
        ARTIST,  // Artist or album artist
        ALBUM    // Album name
    }

    val nameJaroSimilarity = song.name?.let { StringComparison.jaroWinklerMultiDistance(query, it) } ?: StringComparison.JaroSimilarity(0.0, emptyMap(), emptyMap())
    val albumNameJaroSimilarity = song.album?.let { StringComparison.jaroWinklerMultiDistance(query, it) } ?: StringComparison.JaroSimilarity(0.0, emptyMap(), emptyMap())
    val albumArtistNameJaroSimilarity = song.albumArtist?.let { StringComparison.jaroWinklerMultiDistance(query, it) } ?: StringComparison.JaroSimilarity(0.0, emptyMap(), emptyMap())
    val artistNameJaroSimilarity = song.friendlyArtistName?.let { name -> StringComparison.jaroWinklerMultiDistance(query, name) } ?: StringComparison.JaroSimilarity(0.0, emptyMap(), emptyMap())

    /**
     * Composite score using research-backed ranking algorithm.
     *
     * Key improvements over previous implementation:
     * 1. Exact match boost is multiplicative (×2.5) instead of additive (+0.01)
     *    - Research shows 2.0-5.0× is industry standard (Elasticsearch/Solr)
     *    - Ensures exact artist "Tool" ranks above fuzzy song "Toolbox"
     *
     * 2. Increased field weights for secondary fields
     *    - Artist: 0.90 (up from 0.85) - artist matches are important
     *    - Album: 0.85 (up from 0.75) - album matches matter too
     *
     * 3. DisMax tie-breaker scoring (optional, currently 0.0)
     *    - Rewards items that match multiple fields
     *    - Can be tuned from 0.0 (only best field) to 0.3 (30% bonus from other fields)
     *
     * Example scores with 2.5× exact match multiplier:
     * - Song "Sober" by Tool (exact artist): 1.0 × 0.90 × 2.5 = 2.25
     * - Song "Toolbox Blues" (fuzzy name 0.90): 0.90 × 1.0 = 0.90
     * - Song "Help!" by Beatles (exact name + fuzzy artist): 2.5 + (0.3 × 0.8) = 2.74
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

        val albumScoreRaw = albumNameJaroSimilarity.score
        val albumScoreWithBoost = if (albumScoreRaw >= 0.999) {
            albumScoreRaw * exactMatchMultiplier
        } else {
            albumScoreRaw
        }

        // Apply field weights (increased from 0.85/0.75 to 0.90/0.85)
        val nameScore = nameScoreWithBoost * 1.0   // Primary field
        val artistScore = artistScoreWithBoost * 0.90  // Secondary (up from 0.85)
        val albumScore = albumScoreWithBoost * 0.85   // Tertiary (up from 0.75)

        // DisMax scoring: best match + tie-breaker bonus for other fields
        val allScores = listOf(
            Triple(nameScore, MatchedField.NAME, nameJaroSimilarity),
            Triple(artistScore, MatchedField.ARTIST, if (artistScoreRaw == artistNameJaroSimilarity.score) artistNameJaroSimilarity else albumArtistNameJaroSimilarity),
            Triple(albumScore, MatchedField.ALBUM, albumNameJaroSimilarity)
        ).sortedByDescending { it.first }

        val bestScore = allScores[0].first
        val otherScoresSum = allScores.drop(1).sumOf { it.first }

        bestScore + (tieBreaker * otherScoresSum)
    }

    /**
     * Which field had the best match (for highlighting in UI).
     * Determined by which field contributed most to the composite score.
     */
    val matchedField: MatchedField by lazy {
        val allScores = listOf(
            Pair(nameScore_internal, MatchedField.NAME),
            Pair(artistScore_internal, MatchedField.ARTIST),
            Pair(albumScore_internal, MatchedField.ALBUM)
        ).sortedByDescending { it.first }

        allScores[0].second
    }

    /**
     * The matched indices for the best-matched field.
     * Maps character index to match quality (for highlighting).
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
            MatchedField.ALBUM -> albumNameJaroSimilarity.bMatchedIndices
        }
    }

    // Internal scores for matchedField computation
    private val nameScore_internal by lazy {
        val exactMatchMultiplier = 2.5
        val nameScoreRaw = nameJaroSimilarity.score
        val nameScoreWithBoost = if (nameScoreRaw >= 0.999) nameScoreRaw * exactMatchMultiplier else nameScoreRaw
        nameScoreWithBoost * 1.0
    }

    private val artistScore_internal by lazy {
        val exactMatchMultiplier = 2.5
        val artistScoreRaw = max(artistNameJaroSimilarity.score, albumArtistNameJaroSimilarity.score)
        val artistScoreWithBoost = if (artistScoreRaw >= 0.999) artistScoreRaw * exactMatchMultiplier else artistScoreRaw
        artistScoreWithBoost * 0.90
    }

    private val albumScore_internal by lazy {
        val exactMatchMultiplier = 2.5
        val albumScoreRaw = albumNameJaroSimilarity.score
        val albumScoreWithBoost = if (albumScoreRaw >= 0.999) albumScoreRaw * exactMatchMultiplier else albumScoreRaw
        albumScoreWithBoost * 0.85
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
