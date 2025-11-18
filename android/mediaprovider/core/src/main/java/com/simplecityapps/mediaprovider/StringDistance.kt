package com.simplecityapps.mediaprovider

import kotlin.math.min

/**
 * Fast string distance algorithms for fuzzy matching.
 * Used only as Tier 3 fallback on small candidate sets (<100 items).
 */
object StringDistance {
    /**
     * Levenshtein distance: minimum number of single-character edits.
     * Simpler and faster than Jaro-Winkler for typo detection.
     *
     * Examples:
     * - levenshteinDistance("beatles", "beatels") = 2 (swap t and e, swap e and l)
     * - levenshteinDistance("zeppelin", "zepplin") = 1 (delete i)
     *
     * Complexity: O(m*n) but with early termination optimization
     *
     * @param a First string
     * @param b Second string
     * @param maxDistance Early termination if distance > maxDistance
     * @return Edit distance, or Int.MAX_VALUE if > maxDistance
     */
    fun levenshteinDistance(
        a: String,
        b: String,
        maxDistance: Int = 3
    ): Int {
        val aLower = a.lowercase()
        val bLower = b.lowercase()

        if (aLower == bLower) return 0

        val m = aLower.length
        val n = bLower.length

        // Early termination: if length difference > maxDistance
        if (kotlin.math.abs(m - n) > maxDistance) return Int.MAX_VALUE

        // Use two rows instead of full matrix for space efficiency
        var prev = IntArray(n + 1) { it }
        var curr = IntArray(n + 1)

        for (i in 1..m) {
            curr[0] = i
            var minInRow = i

            for (j in 1..n) {
                val cost = if (aLower[i - 1] == bLower[j - 1]) 0 else 1
                curr[j] = min(
                    min(curr[j - 1] + 1, prev[j] + 1), // insert, delete
                    prev[j - 1] + cost // substitute
                )
                minInRow = min(minInRow, curr[j])
            }

            // Early termination: if minimum in row > maxDistance
            if (minInRow > maxDistance) return Int.MAX_VALUE

            // Swap rows
            val temp = prev
            prev = curr
            curr = temp
        }

        return prev[n]
    }

    /**
     * Checks if string 'a' fuzzy-matches string 'b' within tolerance.
     *
     * @param a Query string
     * @param b Target string
     * @param maxEdits Maximum allowed edit distance (default 2)
     * @return true if match within tolerance
     */
    fun fuzzyMatches(
        a: String,
        b: String,
        maxEdits: Int = 2
    ): Boolean = levenshteinDistance(a, b, maxEdits) <= maxEdits

    /**
     * Normalized similarity score (0.0 to 1.0) based on Levenshtein distance.
     *
     * score = 1.0 - (distance / maxLength)
     *
     * Examples:
     * - similarity("beatles", "beatles") = 1.0
     * - similarity("beatles", "beatels") = 0.71 (2 edits / 7 length)
     * - similarity("beatles", "stones") = 0.0 (no match)
     *
     * @return Similarity score 0.0-1.0
     */
    fun similarity(a: String, b: String): Double {
        val distance = levenshteinDistance(a, b, maxDistance = Int.MAX_VALUE)
        if (distance == Int.MAX_VALUE) return 0.0

        val maxLength = kotlin.math.max(a.length, b.length)
        if (maxLength == 0) return 1.0

        return 1.0 - (distance.toDouble() / maxLength)
    }
}
