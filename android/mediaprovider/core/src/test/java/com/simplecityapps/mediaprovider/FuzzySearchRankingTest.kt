package com.simplecityapps.mediaprovider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Critical ranking tests that expose how the algorithm handles ambiguous cases.
 * These tests make hard decisions about what SHOULD rank higher based on user expectations.
 *
 * Many of these tests may FAIL initially - that's the point! They reveal cases where
 * the algorithm behavior might not match user expectations.
 */
class FuzzySearchRankingTest {

    private data class RankedResult(val name: String, val score: Double)

    private fun rankResults(query: String, targets: List<String>): List<RankedResult> = targets
        .map { target ->
            val similarity = StringComparison.jaroWinklerMultiDistance(query, target)
            RankedResult(target, similarity.score)
        }
        .sortedWith(
            compareByDescending<RankedResult> { it.score }
                .thenBy { stripArticlesForSorting(it.name).length }
        )

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

    private fun assertRankingOrder(
        query: String,
        targets: List<String>,
        expectedOrder: List<String>,
        message: String
    ) {
        val ranked = rankResults(query, targets)
        val actualOrder = ranked.map { it.name }

        expectedOrder.forEachIndexed { index, expected ->
            assertEquals(
                "$message\nExpected '$expected' at position $index for query '$query'.\n" +
                    "Actual ranking: ${ranked.map { "${it.name}(${String.format("%.3f", it.score)})" }}",
                expected,
                actualOrder.getOrNull(index)
            )
        }
    }

    // ===================================================================================
    // EXACT SHORT MATCH VS LONG PARTIAL MATCH
    // ===================================================================================

    @Test
    fun `CRITICAL - exact match should beat partial match in longer string`() {
        // User types "red" - there's a band literally called "Red"
        // Should it beat "Red Hot Chili Peppers"?
        val targets = listOf("Red", "Red Hot Chili Peppers", "Simply Red")

        val ranked = rankResults("red", targets)

        // User expectation: Exact match "Red" should rank first
        // STRICT TEST: "Red" is exact match, should beat all partials
        assertEquals(
            "Expected 'Red' to rank first for query 'red' (exact match beats partial).\n" +
                "Rankings: ${ranked.map { "${it.name}(${String.format("%.3f", it.score)})" }}",
            "Red",
            ranked[0].name
        )
    }

    @Test
    fun `CRITICAL - exact match beats substring match`() {
        // "queen" should match the band "Queen" better than "Queens of the Stone Age"
        val targets = listOf("Queen", "Queens of the Stone Age", "Queensrÿche")

        assertRankingOrder(
            "queen",
            targets,
            listOf("Queen"), // Only asserting #1 position
            "Exact single-word match should rank highest"
        )
    }

    @Test
    fun `exact match beats fuzzy match`() {
        val targets = listOf("The Beatles", "Beat Happening", "Beartooth")

        // "beatles" is exact (ignoring "The"), should beat "beat" prefix matches
        assertRankingOrder(
            "beatles",
            targets,
            listOf("The Beatles"),
            "Exact match should beat prefix matches"
        )
    }

    // ===================================================================================
    // SUBSTRING POSITION MATTERS
    // ===================================================================================

    @Test
    fun `word boundary matches should rank higher than mid-word matches`() {
        val targets = listOf(
            "The Man",
            "Manchester Orchestra",
            "Iron Man",
            "Human League", // "man" is MID-WORD here
            "Manhattans"
        )

        val ranked = rankResults("man", targets)

        // Complete word matches should beat partial word matches
        val completeWordMatches = setOf("The Man", "Iron Man", "Manchester Orchestra", "Manhattans")
        val top3 = ranked.take(3).map { it.name }

        // At least 2 of top 3 should be complete word matches
        val completeWordInTop3 = top3.count { it in completeWordMatches }
        assertTrue(
            "Expected at least 2 complete word matches in top 3 for 'man'.\n" +
                "Got: ${ranked.map { "${it.name}(${String.format("%.3f", it.score)})" }}",
            completeWordInTop3 >= 2
        )
    }

    @Test
    fun `prefix match should rank higher than suffix match`() {
        val targets = listOf(
            "Manchester Orchestra", // Prefix
            "The Man", // Complete word
            "Iron Man" // Suffix
        )

        val ranked = rankResults("man", targets)

        // STRICT TEST: Complete word match should beat prefix and suffix
        assertEquals(
            "Expected 'The Man' to rank first for 'man' (complete word match).\n" +
                "Rankings: ${ranked.map { "${it.name}(${String.format("%.3f", it.score)})" }}",
            "The Man",
            ranked[0].name
        )
    }

    // ===================================================================================
    // AMBIGUOUS QUERIES - FAMOUS VS OBSCURE
    // ===================================================================================

    @Test
    fun `CRITICAL - partial query prefers most specific match`() {
        // "beat" - what should rank first?
        val targets = listOf("The Beatles", "Beat Happening", "Beartooth", "Beatnuts")

        val ranked = rankResults("beat", targets)

        // User expectation: Most users typing "beat" want "The Beatles"
        // "Beat Happening" has exact prefix but Beatles is more likely intent
        // STRICT TEST: Assert Beatles should be #1
        assertEquals(
            "Expected 'The Beatles' to rank first for 'beat' (more famous, likely user intent).\n" +
                "Rankings: ${ranked.map { "${it.name}(${String.format("%.3f", it.score)})" }}",
            "The Beatles",
            ranked[0].name
        )
    }

    @Test
    fun `CRITICAL - partial query with common prefix`() {
        // "metal" - Metallica vs Metal Church?
        val targets = listOf("Metallica", "Metal Church", "Metronomy")

        val ranked = rankResults("metal", targets)

        // "Metallica" has "metal" as prefix and is more famous
        // "Metal Church" has "Metal" as complete WORD
        // STRICT TEST: Metallica should win (exact prefix match + popularity)
        assertEquals(
            "Expected 'Metallica' to rank first for 'metal' (exact prefix, more famous).\n" +
                "Rankings: ${ranked.map { "${it.name}(${String.format("%.3f", it.score)})" }}",
            "Metallica",
            ranked[0].name
        )
    }

    // ===================================================================================
    // COMMON MISSPELLINGS
    // ===================================================================================

    @Test
    fun `common misspelling should match well`() {
        // "beetles" is a VERY common misspelling of "Beatles"
        val targets = listOf("The Beatles", "Needles", "Betties")

        assertRankingOrder(
            "beetles",
            targets,
            listOf("The Beatles"),
            "Common misspelling should still match correctly"
        )
    }

    @Test
    fun `common misspelling - nirvanna`() {
        // Double 'n' is common mistake
        // However, if there's actually a band called "Nirvanna", it should rank first
        // The algorithm can't know "Nirvana" is more famous without external data
        val targets = listOf("Nirvana", "Nirvanna", "Anna", "Havana")

        val ranked = rankResults("nirvanna", targets)

        // STRICT TEST: "Nirvanna" is exact match, should rank #1
        // (Without popularity data, exact match beats close match)
        assertEquals(
            "Expected 'Nirvanna' to rank first for query 'nirvanna' (exact match).\n" +
                "Rankings: ${ranked.map { "${it.name}(${String.format("%.3f", it.score)})" }}",
            "Nirvanna",
            ranked[0].name
        )

        // Both should be in top 2
        val top2 = ranked.take(2).map { it.name }
        assertTrue(
            "Expected both 'Nirvana' and 'Nirvanna' in top 2.\nGot: $top2",
            top2.containsAll(listOf("Nirvana", "Nirvanna"))
        )
    }

    // ===================================================================================
    // PROGRESSIVE TYPING STABILITY
    // ===================================================================================

    @Test
    fun `CRITICAL - progressive typing should maintain stable top result`() {
        val targets = listOf("The Beatles", "Beat Happening", "Beach Boys", "Beartooth")

        val progressiveQueries = listOf("b", "be", "bea", "beat", "beatl", "beatle", "beatles")

        progressiveQueries.forEach { query ->
            val ranked = rankResults(query, targets)

            // Early queries ("bea") will favor prefix matches like "Beat Happening"
            // That's expected! Can't expect "beatles" to rank first for query "bea"

            // However, by "beat", The Beatles should be in top 2
            if (query.length >= 4) {
                assertTrue(
                    "Expected 'The Beatles' in top 2 for progressive query '$query'.\n" +
                        "Rankings: ${ranked.map { "${it.name}(${String.format("%.3f", it.score)})" }}",
                    ranked.take(2).any { it.name == "The Beatles" }
                )
            }
        }

        // By "beatle" (6 chars), it MUST be #1
        // Note: "beatl" (5 chars) still favors "Beat Happening" due to strong prefix match
        assertRankingOrder(
            "beatle",
            targets,
            listOf("The Beatles"),
            "Specific query should rank correct result first"
        )

        assertRankingOrder(
            "beatles",
            targets,
            listOf("The Beatles"),
            "Full query should definitely rank correct result first"
        )
    }

    // ===================================================================================
    // MULTI-WORD QUERY PRIORITY
    // ===================================================================================

    @Test
    fun `CRITICAL - multi-word query both words should matter`() {
        // "queen stone" - should strongly prefer "Queens of the Stone Age"
        val targets = listOf("Queen", "Queens of the Stone Age", "Stone Temple Pilots")

        val ranked = rankResults("queen stone", targets)

        // Result with BOTH words should rank higher than result with just one
        assertEquals(
            "Expected 'Queens of the Stone Age' to rank first (has both 'queen' and 'stone').\n" +
                "Rankings: ${ranked.map { "${it.name}(${String.format("%.3f", it.score)})" }}",
            "Queens of the Stone Age",
            ranked[0].name
        )
    }

    @Test
    fun `multi-word query word order flexibility`() {
        val targets = listOf("Red Hot Chili Peppers", "Hot Chip", "Red House Painters")

        // "hot red" should still match "Red Hot Chili Peppers" best
        assertRankingOrder(
            "hot red",
            targets,
            listOf("Red Hot Chili Peppers"),
            "Reordered words should still find best match"
        )
    }

    // ===================================================================================
    // LENGTH AND COMPLETENESS BIAS
    // ===================================================================================

    @Test
    fun `shorter complete match vs longer partial match`() {
        val targets = listOf(
            "U2",
            "UB40",
            "U-God",
            "U2 Live"
        )

        // "u2" should strongly prefer exact "U2"
        assertRankingOrder(
            "u2",
            targets,
            listOf("U2"),
            "Exact short match should beat partial matches"
        )
    }

    @Test
    fun `acronym vs full name`() {
        val targets = listOf("NIN", "Nine Inch Nails", "Ninth Wonder")

        // This is interesting: should "nin" match "NIN" or "Nine Inch Nails" better?
        val rankedNin = rankResults("nin", targets)

        // STRICT TEST: "NIN" is exact match (case-insensitive), should beat partial
        assertEquals(
            "Expected 'NIN' to rank first for 'nin' (exact match beats partial word match).\n" +
                "Rankings: ${rankedNin.map { "${it.name}(${String.format("%.3f", it.score)})" }}",
            "NIN",
            rankedNin[0].name
        )
    }

    // ===================================================================================
    // REAL AMBIGUOUS CASES
    // ===================================================================================

    @Test
    fun `CRITICAL - black prefix with many matches`() {
        val targets = listOf(
            "Black Sabbath",
            "The Black Keys",
            "Black Flag",
            "Blackpink",
            "Black Veil Brides",
            "Black Crowes"
        )

        val ranked = rankResults("black", targets)

        // All should score highly - this is genuinely ambiguous
        // But let's verify they're all above threshold
        ranked.forEach { result ->
            assertTrue(
                "Expected '${result.name}' to match 'black' above threshold. Score: ${result.score}",
                result.score > 0.80 // Slightly lower threshold due to "The" in "The Black Keys"
            )
        }

        // Verify no result completely dominates
        val topScore = ranked[0].score
        val secondScore = ranked[1].score
        val scoreDiff = topScore - secondScore

        assertTrue(
            "Ambiguous query 'black' should not have one result dominating (score diff > 0.2).\n" +
                "Top scores: ${ranked.take(3).map { "${it.name}(${String.format("%.3f", it.score)})" }}",
            scoreDiff < 0.2
        )
    }

    @Test
    fun `the prefix with many matches`() {
        val targets = listOf(
            "The Beatles",
            "The Who",
            "The Doors",
            "The Killers",
            "The National",
            "The Strokes"
        )

        val ranked = rankResults("the", targets)

        // All should match very similarly since "the" is in all of them
        // This tests that we don't artificially prefer one
        val scores = ranked.map { it.score }
        val maxScore = scores.maxOrNull() ?: 0.0
        val minScore = scores.minOrNull() ?: 0.0

        assertTrue(
            "Query 'the' should match all bands with 'The' similarly (score range < 0.1).\n" +
                "Scores: ${ranked.map { "${it.name}(${String.format("%.3f", it.score)})" }}",
            (maxScore - minScore) < 0.1
        )
    }

    // ===================================================================================
    // SCORE SANITY CHECKS
    // ===================================================================================

    @Test
    fun `scores should be distributed not clustered`() {
        // Test that we're not just returning the same score for everything
        val targets = listOf(
            "The Beatles", // Should match "beat" well
            "Beach Boys", // Should match "beat" moderately
            "Beethoven", // Should match "beat" poorly
            "Pink Floyd" // Should match "beat" very poorly
        )

        val ranked = rankResults("beat", targets)

        // Verify scores are actually different (not all ~0.85 or ~1.0)
        val uniqueScores = ranked.map { String.format("%.2f", it.score) }.distinct().size
        assertTrue(
            "Expected varied scores for 'beat', got $uniqueScores unique values.\n" +
                "Rankings: ${ranked.map { "${it.name}(${String.format("%.3f", it.score)})" }}",
            uniqueScores >= 3
        )
    }

    @Test
    fun `poor matches should score significantly lower than good matches`() {
        val targets = listOf("The Beatles", "Pink Floyd")

        val beatlesScore = StringComparison.jaroWinklerMultiDistance("beatles", "The Beatles").score
        val floydScore = StringComparison.jaroWinklerMultiDistance("beatles", "Pink Floyd").score

        assertTrue(
            "Good match should score significantly higher than poor match.\n" +
                "Beatles: $beatlesScore, Floyd: $floydScore",
            beatlesScore - floydScore > 0.3
        )
    }
}
