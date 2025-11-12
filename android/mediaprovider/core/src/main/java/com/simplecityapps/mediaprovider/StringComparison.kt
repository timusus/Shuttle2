package com.simplecityapps.mediaprovider

import com.simplecityapps.mediaprovider.StringComparison.jaroDistance
import java.text.Normalizer
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

object StringComparison {
    /**
     * Default similarity threshold for search results.
     * Lowered from 0.90 to 0.85 to allow more partial matches
     * (e.g., "beatles" matching "The Beatles", "zeppelin" matching "Led Zeppelin")
     */
    const val threshold = 0.85

    /**
     * Definite and indefinite articles by locale.
     * Only articles followed by whitespace will be stripped to preserve names like "A-ha", "La Roux".
     */
    private val ARTICLES_BY_LOCALE = mapOf(
        // English
        "en" to listOf("the", "a", "an"),
        // Spanish
        "es" to listOf("el", "la", "los", "las", "un", "una", "unos", "unas"),
        // French
        "fr" to listOf("le", "la", "les", "l", "un", "une", "des"),
        // German
        "de" to listOf("der", "die", "das", "den", "dem", "des", "ein", "eine", "einen", "einem", "einer"),
        // Italian
        "it" to listOf("il", "lo", "la", "i", "gli", "le", "un", "uno", "una"),
        // Portuguese
        "pt" to listOf("o", "a", "os", "as", "um", "uma", "uns", "umas"),
        // Dutch
        "nl" to listOf("de", "het", "een")
    )

    /**
     * Strips leading articles from a string based on the system locale.
     * Only strips if article is followed by whitespace (preserves "A-ha", "La Roux", etc.).
     *
     * Examples:
     * - "The Beatles" → "Beatles"
     * - "A-ha" → "A-ha" (hyphen, not whitespace)
     * - "Los Lobos" → "Lobos" (Spanish locale)
     * - "La Roux" → "La Roux" if treated as name (depends on usage)
     */
    private fun stripArticles(s: String, locale: Locale = Locale.getDefault()): String {
        val normalized = s.lowercase(locale).trim()

        // Get articles for this locale (fall back to English if locale not supported)
        val languageCode = locale.language
        val articles = ARTICLES_BY_LOCALE[languageCode] ?: ARTICLES_BY_LOCALE["en"]!!

        // Try each article
        for (article in articles) {
            // Only match if article is followed by whitespace (not hyphen, apostrophe, etc.)
            val pattern = "^$article\\s+"
            if (normalized.matches(Regex(pattern + ".*"))) {
                return normalized.replaceFirst(Regex(pattern), "")
            }
        }

        return normalized
    }

    /**
     * @param score A decimal representing the similarity of two strings. A value of 1.0 indicates an exact match
     * @param aMatchedIndices the indices of String A which were found to match
     * @param bMatchedIndices the indices of String B which were found to match
     */
    data class JaroSimilarity(
        val score: Double,
        val aMatchedIndices: Map<Int, Double>,
        val bMatchedIndices: Map<Int, Double>
    )

    /**
     * A decimal representing the similarity of two strings. A value of 1.0 indicates an exact match
     * [https://en.wikipedia.org/wiki/Jaro%E2%80%93Winkler_distance](Wiki)
     *
     * @return [JaroSimilarity]
     */
    fun jaroDistance(
        a: String,
        b: String
    ): JaroSimilarity {
        if (a == b) {
            return JaroSimilarity(
                score = 1.0,
                aMatchedIndices = a.mapIndexed { index, _ -> index to 1.0 }.toMap(),
                bMatchedIndices = b.mapIndexed { index, _ -> index to 1.0 }.toMap()
            )
        }

        val aLen = a.length
        val bLen = b.length

        val aMatches = hashMapOf<Int, Char>()
        val bMatches = hashMapOf<Int, Char>()

        // Two characters are considered matching only if they are equal and not farther than this distance apart
        val matchDistance = (max(aLen, bLen) / 2) - 1

        var matches = 0

        for (i in 0 until aLen) {
            val x = max(0, i - matchDistance) // lower bound of our match distance
            val y = min(bLen, i + matchDistance + 1) // upper bound of our match distance
            for (j in x until y) {
                if (a[i] == b[j] && !bMatches.containsKey(j)) {
                    // Chars are equal and we haven't recorded this match before
                    aMatches[i] = a[i]
                    bMatches[j] = b[j]
                    matches++
                    break
                }
            }
        }

        if (matches == 0) {
            return JaroSimilarity(0.0, hashMapOf(), hashMapOf())
        }

        // These are used to arbitrarily keep track of how much each matching character contributed to the overall match
        val aMatchScores = aMatches.map { it.key to 1.0 }.toMap().toMutableMap()
        val bMatchScores = bMatches.map { it.key to 1.0 }.toMap().toMutableMap()

        var transpositions = 0
        var k = 0
        for (i in 0 until aLen) {
            if (aMatches.containsKey(i)) {
                for (j in k until bLen) {
                    if (bMatches.containsKey(j)) {
                        if (aMatches[i] != bMatches[j]) {
                            transpositions++
                            bMatchScores[j] = bMatchScores[j]!! * 0.75
                        }
                        k = j + 1 // watch out for this sneaky fuck
                        break
                    }
                }
            }
        }
        transpositions /= 2

        return JaroSimilarity(
            score = ((matches / aLen.toDouble() + matches / bLen.toDouble() + (matches - transpositions) / matches.toDouble()) / 3.0),
            aMatchedIndices = aMatchScores,
            bMatchedIndices = bMatchScores
        )
    }

    /**
     * A decimal representing the similarity of two strings. A value of 1.0 indicates an exact match
     * This function is derived from [jaroDistance], but weighted in favour of strings whose prefix also match
     * [https://en.wikipedia.org/wiki/Jaro%E2%80%93Winkler_distance](Wiki)
     */
    fun jaroWinklerDistance(
        a: String,
        b: String
    ): JaroSimilarity {
        val a = Normalizer.normalize(a.lowercase(), Normalizer.Form.NFD)
        val b = Normalizer.normalize(b.lowercase(), Normalizer.Form.NFD)

        val jaroSimilarity = jaroDistance(a, b)
        val prefixScale = 0.1

        val aLen = a.length
        val bLen = b.length

        var prefix = 0
        for (i in 0 until aLen.coerceAtMost(bLen)) {
            if (a[i] == b[i]) {
                prefix++
            } else {
                break
            }
        }
        prefix = prefix.coerceAtMost(4)

        return JaroSimilarity(
            score = jaroSimilarity.score + (prefix * prefixScale * (1 - jaroSimilarity.score)),
            aMatchedIndices = jaroSimilarity.aMatchedIndices,
            bMatchedIndices = jaroSimilarity.bMatchedIndices
        )
    }

    /**
     * Enhanced multi-word matching that handles both single and multi-word queries.
     * First attempts to match the full query against the full target.
     * If that doesn't meet the threshold, tries:
     * 1. Matching full query against individual target words
     * 2. Matching individual query words against individual target words (for multi-word queries)
     *
     * For multi-word queries, this function rewards targets that contain multiple query words
     * by applying a coverage bonus to the final score.
     *
     * Additionally, this function:
     * - Strips locale-aware articles ("The", "La", "Der", etc.) to improve matching
     * - Applies prefix boost when query is a prefix of the target (after stripping articles)
     *
     * This allows queries like "dark side" to match "The Dark Side of the Moon",
     * "zeppelin" to match "Led Zeppelin", "beat" to match "The Beatles",
     * and "queen stone" prefers "Queens of the Stone Age" over just "Queen".
     */
    fun jaroWinklerMultiDistance(
        a: String,
        b: String,
        multiWordThreshold: Double = threshold
    ): JaroSimilarity {
        val aSplit = a.split(" ")
        val bSplit = b.split(" ")

        // Collect all possible matching strategies
        val allMatches = mutableListOf<JaroSimilarity>()

        // Strategy 1: Try matching the full strings
        val fullStringMatch = jaroWinklerDistance(a, b)
        allMatches.add(fullStringMatch)

        // Store potential prefix boost for later (only apply if no better match exists)
        val strippedA = stripArticles(a)
        val strippedB = stripArticles(b)
        var potentialPrefixBoost: JaroSimilarity? = null

        // Check if query is a prefix of target (after stripping articles)
        if (strippedB.startsWith(strippedA) &&
            strippedA.isNotEmpty() &&
            strippedA != strippedB
        ) {
            // Calculate prefix-boosted score (but don't add to allMatches yet)
            // Cap at 1.0 so prefix matches can tie with exact matches
            // (rely on secondary sorting by length to break ties)
            val strippedScore = jaroWinklerDistance(strippedA, strippedB).score
            val boostedScore = min(strippedScore + 0.10, 1.0)
            potentialPrefixBoost = fullStringMatch.copy(score = boostedScore)
        }

        // If both are single words, check prefix boost then return best match
        if (aSplit.size == 1 && bSplit.size == 1) {
            var bestMatch = allMatches.maxByOrNull { it.score }!!
            // Apply prefix boost if it improves the score
            if (potentialPrefixBoost != null && bestMatch.score < 0.999 && potentialPrefixBoost.score > bestMatch.score) {
                bestMatch = potentialPrefixBoost
            }
            return bestMatch
        }

        // Strategy 2: Try matching full query against each word in target
        allMatches.addAll(
            bSplit.mapIndexed { bIndex, bWord ->
                val splitSimilarity = jaroWinklerDistance(a, bWord)
                splitSimilarity.copy(
                    aMatchedIndices = splitSimilarity.aMatchedIndices,
                    bMatchedIndices = splitSimilarity.bMatchedIndices.mapKeys {
                        it.key + bIndex + bSplit.take(bIndex).sumOf { it.length }
                    }
                )
            }
        )

        // Strategy 3: If query has multiple words, try matching each query word against each target word
        if (aSplit.size > 1) {
            allMatches.addAll(
                aSplit.flatMapIndexed { aIndex, aWord ->
                    bSplit.mapIndexed { bIndex, bWord ->
                        val splitSimilarity = jaroWinklerDistance(aWord, bWord)
                        splitSimilarity.copy(
                            aMatchedIndices = splitSimilarity.aMatchedIndices.mapKeys {
                                it.key + aIndex + aSplit.take(aIndex).sumOf { it.length }
                            },
                            bMatchedIndices = splitSimilarity.bMatchedIndices.mapKeys {
                                it.key + bIndex + bSplit.take(bIndex).sumOf { it.length }
                            }
                        )
                    }
                }
            )
        }

        // Get the best match from all strategies
        var bestMatch = allMatches.maxByOrNull { it.score }!!

        // Apply prefix boost if it would improve the score
        // Only applies when bestMatch score < 1.0 to avoid boosting already-perfect matches
        if (potentialPrefixBoost != null && bestMatch.score < 0.999) {
            if (potentialPrefixBoost.score > bestMatch.score) {
                bestMatch = potentialPrefixBoost
            }
        }

        // Apply multi-word coverage bonus for multi-word queries
        if (aSplit.size > 1) {
            bestMatch = applyMultiWordCoverageBonus(aSplit, bSplit, bestMatch)
        }

        return bestMatch
    }

    /**
     * Applies a bonus to the score when multiple query words are present in the target.
     *
     * For example, searching "queen stone" should rank "Queens of the Stone Age" higher
     * than just "Queen", because the target contains both query words.
     *
     * The bonus is applied by multiplying the base score by (1 + 0.05 * (matchedQueryWords - 1))
     * This means:
     * - 1 query word matched: score * 1.0 (no change)
     * - 2 query words matched: score * 1.05
     * - 3 query words matched: score * 1.10
     *
     * Note: Using multiplication preserves relative ranking of similar matches while
     * rewarding completeness. This works even when base scores are very high (near 1.0).
     */
    private fun applyMultiWordCoverageBonus(
        queryWords: List<String>,
        targetWords: List<String>,
        baseSimilarity: JaroSimilarity
    ): JaroSimilarity {
        // For each query word, find its best match against any target word
        val queryWordMatches = queryWords.map { queryWord ->
            targetWords.map { targetWord ->
                jaroWinklerDistance(queryWord, targetWord).score
            }.maxOrNull() ?: 0.0
        }

        // Count how many query words found a good match (score >= 0.85)
        val matchedQueryWords = queryWordMatches.count { it >= 0.85 }

        // Apply multiplicative bonus if multiple query words matched
        // This rewards targets that match more query words
        val multiplier = if (matchedQueryWords > 1) {
            1.0 + (0.05 * (matchedQueryWords - 1))
        } else {
            1.0
        }

        val finalScore = baseSimilarity.score * multiplier

        return baseSimilarity.copy(
            score = finalScore
        )
    }
}
