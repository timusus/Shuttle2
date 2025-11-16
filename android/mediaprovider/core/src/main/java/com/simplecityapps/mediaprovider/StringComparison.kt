package com.simplecityapps.mediaprovider

import android.util.Log
import com.simplecityapps.mediaprovider.StringComparison.jaroDistance
import java.text.Normalizer
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

object StringComparison {
    private const val TAG = "StringComparison"
    // Performance logging disabled in production for performance (5-10% overhead)
    // Set to true for development/debugging only
    private const val ENABLE_PERFORMANCE_LOGGING = false

    /**
     * Default similarity threshold for search results.
     * Lowered from 0.90 → 0.85 → 0.82 to allow more fuzzy matches and typos
     * (e.g., "beatels" matching "The Beatles", "zepelin" matching "Led Zeppelin")
     * Combined with FTS fallback, this provides excellent fuzzy search coverage.
     */
    const val threshold = 0.82

    // Performance counters
    @Volatile private var jaroDistanceCallCount = 0
    @Volatile private var jaroWinklerDistanceCallCount = 0
    @Volatile private var jaroWinklerMultiDistanceCallCount = 0
    @Volatile private var totalJaroDistanceTimeNs = 0L
    @Volatile private var totalJaroWinklerDistanceTimeNs = 0L
    @Volatile private var totalJaroWinklerMultiDistanceTimeNs = 0L

    fun resetPerformanceCounters() {
        jaroDistanceCallCount = 0
        jaroWinklerDistanceCallCount = 0
        jaroWinklerMultiDistanceCallCount = 0
        totalJaroDistanceTimeNs = 0L
        totalJaroWinklerDistanceTimeNs = 0L
        totalJaroWinklerMultiDistanceTimeNs = 0L
    }

    fun logPerformanceStats() {
        if (!ENABLE_PERFORMANCE_LOGGING) return

        Log.d(TAG, "=== StringComparison Performance Stats ===")
        Log.d(TAG, "jaroDistance: $jaroDistanceCallCount calls, avg ${if (jaroDistanceCallCount > 0) totalJaroDistanceTimeNs / jaroDistanceCallCount / 1000 else 0}μs, total ${totalJaroDistanceTimeNs / 1_000_000}ms")
        Log.d(TAG, "jaroWinklerDistance: $jaroWinklerDistanceCallCount calls, avg ${if (jaroWinklerDistanceCallCount > 0) totalJaroWinklerDistanceTimeNs / jaroWinklerDistanceCallCount / 1000 else 0}μs, total ${totalJaroWinklerDistanceTimeNs / 1_000_000}ms")
        Log.d(TAG, "jaroWinklerMultiDistance: $jaroWinklerMultiDistanceCallCount calls, avg ${if (jaroWinklerMultiDistanceCallCount > 0) totalJaroWinklerMultiDistanceTimeNs / jaroWinklerMultiDistanceCallCount / 1000 else 0}μs, total ${totalJaroWinklerMultiDistanceTimeNs / 1_000_000}ms")
        val totalTimeMs = (totalJaroDistanceTimeNs + totalJaroWinklerDistanceTimeNs + totalJaroWinklerMultiDistanceTimeNs) / 1_000_000
        Log.d(TAG, "Total computation time: ${totalTimeMs}ms")
    }

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
        val startTime = if (ENABLE_PERFORMANCE_LOGGING) System.nanoTime() else 0L

        if (a == b) {
            if (ENABLE_PERFORMANCE_LOGGING) {
                jaroDistanceCallCount++
                totalJaroDistanceTimeNs += System.nanoTime() - startTime
            }
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

        val result = JaroSimilarity(
            score = ((matches / aLen.toDouble() + matches / bLen.toDouble() + (matches - transpositions) / matches.toDouble()) / 3.0),
            aMatchedIndices = aMatchScores,
            bMatchedIndices = bMatchScores
        )

        if (ENABLE_PERFORMANCE_LOGGING) {
            jaroDistanceCallCount++
            totalJaroDistanceTimeNs += System.nanoTime() - startTime
        }

        return result
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
        val startTime = if (ENABLE_PERFORMANCE_LOGGING) System.nanoTime() else 0L

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

        val result = JaroSimilarity(
            score = jaroSimilarity.score + (prefix * prefixScale * (1 - jaroSimilarity.score)),
            aMatchedIndices = jaroSimilarity.aMatchedIndices,
            bMatchedIndices = jaroSimilarity.bMatchedIndices
        )

        if (ENABLE_PERFORMANCE_LOGGING) {
            jaroWinklerDistanceCallCount++
            totalJaroWinklerDistanceTimeNs += System.nanoTime() - startTime
        }

        return result
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
        val startTime = if (ENABLE_PERFORMANCE_LOGGING) System.nanoTime() else 0L

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
        // Cache these scores to avoid redundant calculations in applyMultiWordCoverageBonus
        val wordToWordScores: Map<Pair<Int, Int>, Double>? = if (aSplit.size > 1) {
            val scoresMap = mutableMapOf<Pair<Int, Int>, Double>()
            allMatches.addAll(
                aSplit.flatMapIndexed { aIndex, aWord ->
                    bSplit.mapIndexed { bIndex, bWord ->
                        val splitSimilarity = jaroWinklerDistance(aWord, bWord)
                        // Cache the score for later use
                        scoresMap[Pair(aIndex, bIndex)] = splitSimilarity.score
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
            scoresMap
        } else {
            null
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
        // This also combines matched indices from all matched words for highlighting
        if (aSplit.size > 1 && wordToWordScores != null) {
            bestMatch = applyMultiWordCoverageBonus(aSplit, bSplit, bestMatch, wordToWordScores, allMatches)
        }

        if (ENABLE_PERFORMANCE_LOGGING) {
            jaroWinklerMultiDistanceCallCount++
            totalJaroWinklerMultiDistanceTimeNs += System.nanoTime() - startTime
        }

        return bestMatch
    }

    /**
     * Applies a bonus to the score when multiple query words are present in the target.
     * Also combines matched indices from all matched words for proper highlighting.
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
     * Additionally, this function now combines the bMatchedIndices from all matched words,
     * so searching "dark side" will highlight both "dark" AND "side" in "The Dark Side".
     *
     * Note: Using multiplication preserves relative ranking of similar matches while
     * rewarding completeness. This works even when base scores are very high (near 1.0).
     *
     * @param wordToWordScores Cached word-to-word similarity scores to avoid redundant calculations.
     *                         Map keys are Pair(queryWordIndex, targetWordIndex).
     * @param allMatches All similarity matches from different strategies, used to extract matched indices.
     */
    private fun applyMultiWordCoverageBonus(
        queryWords: List<String>,
        targetWords: List<String>,
        baseSimilarity: JaroSimilarity,
        wordToWordScores: Map<Pair<Int, Int>, Double>,
        allMatches: List<JaroSimilarity>
    ): JaroSimilarity {
        // For each query word, find its best match against any target word using cached scores
        val queryWordBestMatches = queryWords.mapIndexed { queryIndex, _ ->
            var bestScore = 0.0
            var bestTargetIndex = -1
            targetWords.indices.forEach { targetIndex ->
                val score = wordToWordScores[Pair(queryIndex, targetIndex)] ?: 0.0
                if (score > bestScore) {
                    bestScore = score
                    bestTargetIndex = targetIndex
                }
            }
            Triple(queryIndex, bestTargetIndex, bestScore)
        }

        // Count how many query words found a good match (score >= 0.82, using current threshold)
        val matchedQueryWords = queryWordBestMatches.count { it.third >= threshold }

        // Combine bMatchedIndices from all matched words for highlighting
        val combinedBMatchedIndices = mutableMapOf<Int, Double>()
        if (matchedQueryWords > 1) {
            // Find all word-to-word matches in allMatches and combine their bMatchedIndices
            queryWordBestMatches.filter { it.third >= threshold }.forEach { (queryIndex, targetIndex, _) ->
                // Find the corresponding match in allMatches
                // allMatches structure: [fullStringMatch, ...strategy2Matches, ...strategy3Matches]
                // Strategy 3 starts at index: 1 + targetWords.size
                val matchIndex = 1 + targetWords.size + (queryIndex * targetWords.size + targetIndex)
                if (matchIndex < allMatches.size) {
                    val wordMatch = allMatches[matchIndex]
                    combinedBMatchedIndices.putAll(wordMatch.bMatchedIndices)
                }
            }
        }

        // Apply multiplicative bonus if multiple query words matched
        // This rewards targets that match more query words
        val multiplier = if (matchedQueryWords > 1) {
            1.0 + (0.05 * (matchedQueryWords - 1))
        } else {
            1.0
        }

        val finalScore = baseSimilarity.score * multiplier

        // Use combined indices if we found multiple matches, otherwise keep original
        val finalBMatchedIndices = if (combinedBMatchedIndices.isNotEmpty()) {
            combinedBMatchedIndices
        } else {
            baseSimilarity.bMatchedIndices
        }

        return baseSimilarity.copy(
            score = finalScore,
            bMatchedIndices = finalBMatchedIndices
        )
    }
}
