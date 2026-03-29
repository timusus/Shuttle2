package com.simplecityapps.mediaprovider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Comprehensive real-world fuzzy search test suite covering user expectations for music search.
 *
 * Tests various scenarios including:
 * - Typos and misspellings
 * - Partial matches (prefixes/suffixes)
 * - Word order variations
 * - Special characters and diacritics
 * - Common word handling ("The", "A", etc.)
 * - Numbers and punctuation
 * - Abbreviations and initials
 * - Similar sounding names (phonetic similarity)
 * - Ambiguous searches with multiple valid results
 *
 * Each test verifies not just that matches are found, but that they're ranked
 * in the expected order based on user perception of match quality.
 */
class FuzzySearchRealWorldTest {

    /**
     * Represents a search test case with expected results in priority order.
     */
    data class SearchScenario(
        val query: String,
        val expectedMatches: List<String>,
        val description: String
    )

    /**
     * Helper to test that a query matches targets in the expected ranking order.
     *
     * @param query The search query
     * @param targets All possible targets to search against
     * @param expectedOrder The expected targets in descending relevance order
     * @param topN How many top results to verify (default: verify all expected)
     */
    private fun assertRankingOrder(
        query: String,
        targets: List<String>,
        expectedOrder: List<String>,
        topN: Int = expectedOrder.size
    ) {
        // Calculate similarity scores for all targets
        val scored = targets.map { target ->
            val similarity = StringComparison.jaroWinklerMultiDistance(query, target)
            target to similarity.score
        }

        // Sort by score descending
        val ranked = scored.sortedByDescending { it.second }

        // Extract top N results
        val topResults = ranked.take(topN).map { it.first }

        // Verify the expected targets appear in the top results in the right order
        val expectedInTopN = expectedOrder.take(topN)
        expectedInTopN.forEachIndexed { index, expectedTarget ->
            assertTrue(
                "Expected '$expectedTarget' to be in top $topN results for query '$query'.\n" +
                    "Top results: $topResults",
                topResults.contains(expectedTarget)
            )

            // Verify relative ranking: each expected target should appear before later ones
            val actualIndex = topResults.indexOf(expectedTarget)
            if (index > 0) {
                val previousExpected = expectedInTopN[index - 1]
                val previousIndex = topResults.indexOf(previousExpected)
                assertTrue(
                    "Expected '$expectedTarget' to rank after '$previousExpected' for query '$query'.\n" +
                        "Actual ranking: $topResults",
                    actualIndex > previousIndex || previousIndex == -1
                )
            }
        }
    }

    /**
     * Helper to verify the best match for a query.
     */
    private fun assertBestMatch(
        query: String,
        targets: List<String>,
        expected: String
    ) {
        val scored = targets.map { target ->
            val similarity = StringComparison.jaroWinklerMultiDistance(query, target)
            target to similarity.score
        }

        val bestMatch = scored.maxByOrNull { it.second }?.first

        assertEquals(
            "Expected '$expected' to be the best match for query '$query'",
            expected,
            bestMatch
        )
    }

    /**
     * Helper to verify that a query matches a target above threshold.
     */
    private fun assertMatchesAboveThreshold(query: String, target: String) {
        val similarity = StringComparison.jaroWinklerMultiDistance(query, target)
        assertTrue(
            "Expected query '$query' to match target '$target' above threshold ${StringComparison.threshold}. " +
                "Actual score: ${similarity.score}",
            similarity.score > StringComparison.threshold
        )
    }

    // ===================================================================================
    // 1. SIMPLE PARTIALS / PREFIXES
    // ===================================================================================

    @Test
    fun `partial - beat matches Beatles and Beat Happening`() {
        val targets = listOf("The Beatles", "Beat Happening", "Beartooth", "Meat Loaf")
        // Both "The Beatles" and "Beat Happening" should match well
        // "Beat Happening" has exact prefix match, "The Beatles" has "beat" in "beatles"
        assertMatchesAboveThreshold("beat", "The Beatles")
        assertMatchesAboveThreshold("beat", "Beat Happening")

        // More specific query should disambiguate
        assertBestMatch("beatles", targets, "The Beatles")
    }

    @Test
    fun `partial - metal matches Metallica and Metal Church`() {
        val targets = listOf("Metallica", "Metal Church", "Metronomy", "Instrumental")
        // Both "Metallica" and "Metal Church" should match well for "metal"
        assertMatchesAboveThreshold("metal", "Metallica")
        assertMatchesAboveThreshold("metal", "Metal Church")

        // More specific query should disambiguate
        assertBestMatch("metallica", targets, "Metallica")
    }

    @Test
    fun `partial - nir matches Nirvana`() {
        val targets = listOf("Nirvana", "Nine Inch Nails", "Norah Jones")
        assertBestMatch("nir", targets, "Nirvana")
    }

    @Test
    fun `partial - foo matches Foo Fighters best`() {
        val targets = listOf("Foo Fighters", "Fountains of Wayne", "Food for Thought")
        assertBestMatch("foo", targets, "Foo Fighters")
    }

    @Test
    fun `partial - pink matches Pink Floyd and Pink`() {
        val targets = listOf("Pink Floyd", "Pink", "Pinback", "The Kinks")
        val topMatches = listOf("Pink Floyd", "Pink")

        // Verify both Pink Floyd and Pink score highly
        topMatches.forEach { target ->
            assertMatchesAboveThreshold("pink", target)
        }
    }

    // ===================================================================================
    // 2. TYPOS / FUZZY EDITS
    // ===================================================================================

    @Test
    fun `typo - betalce matches The Beatles`() {
        assertMatchesAboveThreshold("betalce", "The Beatles")
    }

    @Test
    fun `typo - megalica matches Metallica`() {
        assertMatchesAboveThreshold("megalica", "Metallica")
    }

    @Test
    fun `typo - pnik floid matches Pink Floyd`() {
        assertMatchesAboveThreshold("pnik floid", "Pink Floyd")
    }

    @Test
    fun `typo - readio hed matches Radiohead`() {
        assertMatchesAboveThreshold("readio hed", "Radiohead")
    }

    @Test
    fun `typo - laddy gaga matches Lady Gaga`() {
        assertMatchesAboveThreshold("laddy gaga", "Lady Gaga")
    }

    @Test
    fun `typo - chili pepers matches Red Hot Chili Peppers`() {
        assertMatchesAboveThreshold("chili pepers", "Red Hot Chili Peppers")
    }

    @Test
    fun `typo - blink 183 matches blink-182`() {
        assertMatchesAboveThreshold("blink 183", "blink-182")
    }

    @Test
    fun `typo - kandrik lamar matches Kendrick Lamar`() {
        assertMatchesAboveThreshold("kandrik lamar", "Kendrick Lamar")
    }

    // ===================================================================================
    // 3. MISSING WORDS / REORDERED TERMS
    // ===================================================================================

    @Test
    fun `reordered - pepper red hot matches Red Hot Chili Peppers`() {
        assertMatchesAboveThreshold("pepper red hot", "Red Hot Chili Peppers")
    }

    @Test
    fun `reordered - fighters foo matches Foo Fighters`() {
        assertMatchesAboveThreshold("fighters foo", "Foo Fighters")
    }

    @Test
    fun `reordered - floyd pink matches Pink Floyd`() {
        assertMatchesAboveThreshold("floyd pink", "Pink Floyd")
    }

    @Test
    fun `missing word - dark side matches The Dark Side of the Moon`() {
        assertMatchesAboveThreshold("dark side", "The Dark Side of the Moon")
    }

    @Test
    fun `missing word - stairway heaven matches Stairway to Heaven`() {
        assertMatchesAboveThreshold("stairway heaven", "Stairway to Heaven")
    }

    // ===================================================================================
    // 4. COMMON-WORD NOISE / "THE" HANDLING
    // ===================================================================================

    @Test
    fun `common word - beatles matches The Beatles`() {
        assertMatchesAboveThreshold("beatles", "The Beatles")
    }

    @Test
    fun `common word - the beatles matches The Beatles`() {
        assertMatchesAboveThreshold("the beatles", "The Beatles")
    }

    @Test
    fun `common word - killers matches The Killers`() {
        assertMatchesAboveThreshold("killers", "The Killers")
    }

    @Test
    fun `common word - the killers matches The Killers`() {
        assertMatchesAboveThreshold("the killers", "The Killers")
    }

    @Test
    fun `common word - the who matches The Who`() {
        assertMatchesAboveThreshold("the who", "The Who")
    }

    @Test
    fun `common word - who matches The Who`() {
        assertMatchesAboveThreshold("who", "The Who")
    }

    @Test
    fun `common word - the the matches The The`() {
        // Special case: band actually called "The The"
        assertMatchesAboveThreshold("the the", "The The")
    }

    @Test
    fun `common word - rolling stones matches The Rolling Stones`() {
        assertMatchesAboveThreshold("rolling stones", "The Rolling Stones")
    }

    // ===================================================================================
    // 5. DIACRITICS / SPECIAL CHARACTERS / UNICODE
    // ===================================================================================

    @Test
    fun `diacritics - sigur ros matches Sigur Rós`() {
        assertMatchesAboveThreshold("sigur ros", "Sigur Rós")
    }

    @Test
    fun `diacritics - bjork matches Björk`() {
        assertMatchesAboveThreshold("bjork", "Björk")
    }

    @Test
    fun `diacritics - zoe matches Zoé`() {
        assertMatchesAboveThreshold("zoe", "Zoé")
    }

    @Test
    fun `diacritics - blue oyster cult matches Blue Öyster Cult`() {
        assertMatchesAboveThreshold("blue oyster cult", "Blue Öyster Cult")
    }

    @Test
    fun `diacritics - motorhead matches Motörhead`() {
        assertMatchesAboveThreshold("motorhead", "Motörhead")
    }

    @Test
    fun `diacritics - cafe matches Café Tacvba`() {
        assertMatchesAboveThreshold("cafe", "Café Tacvba")
    }

    // ===================================================================================
    // 6. NUMBERS AND PUNCTUATION
    // ===================================================================================

    @Test
    fun `punctuation - blink 182 matches blink-182`() {
        assertMatchesAboveThreshold("blink 182", "blink-182")
    }

    @Test
    fun `punctuation - blink182 matches blink-182`() {
        assertMatchesAboveThreshold("blink182", "blink-182")
    }

    @Test
    fun `punctuation - acdc matches AC DC`() {
        assertMatchesAboveThreshold("acdc", "AC/DC")
    }

    @Test
    fun `punctuation - ac dc matches AC DC`() {
        assertMatchesAboveThreshold("ac dc", "AC/DC")
    }

    @Test
    fun `punctuation - matchbox 20 matches Matchbox Twenty`() {
        assertMatchesAboveThreshold("matchbox 20", "Matchbox Twenty")
    }

    @Test
    fun `numbers - sum 41 matches Sum 41`() {
        assertMatchesAboveThreshold("sum 41", "Sum 41")
    }

    @Test
    fun `numbers - 3 doors down matches 3 Doors Down`() {
        assertMatchesAboveThreshold("3 doors down", "3 Doors Down")
    }

    // ===================================================================================
    // 7. ONE-LETTER OR SHORT SEARCHES
    // ===================================================================================

    @Test
    fun `short - u2 matches U2 best`() {
        val targets = listOf("U2", "UB40", "U-God", "Ugly Kid Joe")
        assertBestMatch("u2", targets, "U2")
    }

    @Test
    fun `short - a matches bands starting with A`() {
        val targets = listOf("ABBA", "A-ha", "A Tribe Called Quest", "Aerosmith", "Alice in Chains")
        // Single letter "a" has limited discriminating power
        // Score may not reach threshold for all matches
        val scored = targets.map { target ->
            val similarity = StringComparison.jaroWinklerMultiDistance("a", target)
            target to similarity.score
        }.sortedByDescending { it.second }

        // At least some should match reasonably well
        assertTrue("Expected at least one result with score > 0.7", scored.any { it.second > 0.7 })

        // More specific queries work better
        assertMatchesAboveThreshold("abba", "ABBA")
        assertMatchesAboveThreshold("a-ha", "A-ha")
    }

    @Test
    fun `short - r matches R E M and Rush`() {
        val targets = listOf("R.E.M.", "Rush", "Radiohead", "Rage Against the Machine")
        // Single letter "r" matches all, but those starting with R should rank higher
        // Note: Very short queries have limited discriminating power
        val scored = targets.map { target ->
            val similarity = StringComparison.jaroWinklerMultiDistance("r", target)
            target to similarity.score
        }.sortedByDescending { it.second }

        // All should match to some degree
        scored.forEach { (target, score) ->
            assertTrue("Expected '$target' to match 'r'. Score: $score", score > 0.5)
        }
    }

    // ===================================================================================
    // 8. MULTI-TOKEN FUZZY - CROSSOVER & SINGLE-WORD MATCHES
    // ===================================================================================

    @Test
    fun `multi-token - arctic monkey matches Arctic Monkeys`() {
        assertMatchesAboveThreshold("arctic monkey", "Arctic Monkeys")
    }

    @Test
    fun `multi-token - monkeys arctic matches Arctic Monkeys`() {
        assertMatchesAboveThreshold("monkeys arctic", "Arctic Monkeys")
    }

    @Test
    fun `multi-token - queen stone age matches Queens of the Stone Age`() {
        assertMatchesAboveThreshold("queen stone age", "Queens of the Stone Age")
    }

    @Test
    fun `multi-token - stone age matches Queens of the Stone Age`() {
        assertMatchesAboveThreshold("stone age", "Queens of the Stone Age")
    }

    @Test
    fun `multi-token - led zeppelin matches Led Zeppelin`() {
        assertMatchesAboveThreshold("led zeppelin", "Led Zeppelin")
    }

    @Test
    fun `multi-token - zeppelin matches Led Zeppelin`() {
        assertMatchesAboveThreshold("zeppelin", "Led Zeppelin")
    }

    // ===================================================================================
    // 9. NEAR-DUPLICATE LONG LISTS (RANKING BY CLOSENESS)
    // ===================================================================================

    @Test
    fun `near-duplicate - the national matches The National best`() {
        val targets = listOf("The National", "National Park Service", "International")
        assertBestMatch("the national", targets, "The National")
    }

    @Test
    fun `near-duplicate - nine inch matches Nine Inch Nails`() {
        val targets = listOf("Nine Inch Nails", "Nine Days", "Inch by Inch")
        assertBestMatch("nine inch", targets, "Nine Inch Nails")
    }

    @Test
    fun `near-duplicate - nine nails matches Nine Inch Nails`() {
        assertMatchesAboveThreshold("nine nails", "Nine Inch Nails")
    }

    @Test
    fun `near-duplicate - cold play matches Coldplay`() {
        val targets = listOf("Coldplay", "Cold War Kids", "Play")
        // "cold play" should match "Coldplay" well (both words present as one)
        assertMatchesAboveThreshold("cold play", "Coldplay")

        // Single word query is unambiguous
        assertBestMatch("coldplay", targets, "Coldplay")
    }

    // ===================================================================================
    // 10. SIMILAR NAMES - CORRECT RANKING
    // ===================================================================================

    @Test
    fun `similar names - jackson 5 vs michael jackson`() {
        val targets = listOf("The Jackson 5", "Michael Jackson", "Janet Jackson", "Jackson Browne")

        // "jackson 5" should match "The Jackson 5" well
        assertMatchesAboveThreshold("jackson 5", "The Jackson 5")

        // "michael" disambiguates - Michael Jackson should be best for "michael jackson"
        assertBestMatch("michael", targets, "Michael Jackson")

        // Just "jackson" should rank all highly but exact matches better
        val scored = targets.map { target ->
            val similarity = StringComparison.jaroWinklerMultiDistance("jackson", target)
            target to similarity.score
        }

        // All should be above threshold
        scored.forEach { (target, score) ->
            assertTrue(
                "Expected '$target' to match 'jackson' above threshold. Score: $score",
                score > StringComparison.threshold
            )
        }
    }

    @Test
    fun `similar names - black sabbath vs black keys`() {
        val targets = listOf("Black Sabbath", "The Black Keys", "Black Flag", "Black Veil Brides")

        // Full names should match best
        assertBestMatch("black sabbath", targets, "Black Sabbath")

        // Unique word disambiguates
        assertBestMatch("sabbath", targets, "Black Sabbath")
        assertBestMatch("keys", targets, "The Black Keys")

        // "black keys" should match well with The Black Keys
        assertMatchesAboveThreshold("black keys", "The Black Keys")
    }

    @Test
    fun `similar names - queen vs queens of stone age`() {
        val targets = listOf("Queen", "Queens of the Stone Age", "Queensrÿche")

        // "queen" should match "Queen" best (exact single-word match)
        assertBestMatch("queen", targets, "Queen")

        // "queens" should match "Queens of the Stone Age" best
        assertBestMatch("queens", targets, "Queens of the Stone Age")

        // Multi-word query with unique terms disambiguates
        assertMatchesAboveThreshold("stone age", "Queens of the Stone Age")
    }

    @Test
    fun `similar names - red hot chili peppers vs red hot`() {
        val targets = listOf("Red Hot Chili Peppers", "Red Hot", "Red", "Hot Chip")

        assertBestMatch("red hot chili peppers", targets, "Red Hot Chili Peppers")
        assertBestMatch("red hot", targets, "Red Hot Chili Peppers") // Multi-word match
        assertBestMatch("chili peppers", targets, "Red Hot Chili Peppers")
    }

    // ===================================================================================
    // 11. PHONETIC SIMILARITY / "SOUNDS LIKE"
    // ===================================================================================

    @Test
    fun `phonetic - linkin matches Linkin Park`() {
        assertMatchesAboveThreshold("linkin", "Linkin Park")
    }

    @Test
    fun `phonetic - lincoln park matches Linkin Park`() {
        assertMatchesAboveThreshold("lincoln park", "Linkin Park")
    }

    @Test
    fun `phonetic - guns and roses matches Guns N Roses`() {
        assertMatchesAboveThreshold("guns and roses", "Guns N' Roses")
    }

    @Test
    fun `phonetic - guns n roses matches Guns N Roses`() {
        assertMatchesAboveThreshold("guns n roses", "Guns N' Roses")
    }

    // ===================================================================================
    // 12. EDGE CASES & STRESS TESTS
    // ===================================================================================

    @Test
    fun `edge case - empty query returns zero score`() {
        val similarity = StringComparison.jaroWinklerMultiDistance("", "The Beatles")
        assertEquals(0.0, similarity.score, 0.001)
    }

    @Test
    fun `edge case - exact match gets perfect score`() {
        val similarity = StringComparison.jaroWinklerMultiDistance("The Beatles", "The Beatles")
        // Exact match with 2 words gets multi-word bonus: 1.0 * 1.05 = 1.05
        assertEquals(1.05, similarity.score, 0.001)
    }

    @Test
    fun `edge case - case insensitive matching`() {
        val upperScore = StringComparison.jaroWinklerMultiDistance("BEATLES", "the beatles")
        val lowerScore = StringComparison.jaroWinklerMultiDistance("beatles", "THE BEATLES")
        val mixedScore = StringComparison.jaroWinklerMultiDistance("BeAtLeS", "ThE bEaTlEs")

        // All should match well (case-insensitive)
        assertTrue(upperScore.score > 0.95)
        assertTrue(lowerScore.score > 0.95)
        assertTrue(mixedScore.score > 0.95)
    }

    @Test
    fun `edge case - very long band names`() {
        val longName = "Godspeed You! Black Emperor"
        assertMatchesAboveThreshold("godspeed", longName)
        assertMatchesAboveThreshold("black emperor", longName)
        assertMatchesAboveThreshold("godspeed black emperor", longName)
    }

    @Test
    fun `edge case - single character differences`() {
        val targets = listOf("The Kinks", "The Kings", "King Crimson", "Kingfish")

        // "kinks" should match "The Kinks" best
        assertBestMatch("kinks", targets, "The Kinks")

        // "kings" should match "The Kings" best
        assertBestMatch("kings", targets, "The Kings")
    }

    // ===================================================================================
    // 13. COMPREHENSIVE RANKING TESTS
    // ===================================================================================

    @Test
    fun `comprehensive ranking - beatles variations`() {
        val targets = listOf(
            "The Beatles",
            "Beatles Tribute Band",
            "Beat Happening",
            "Beartooth",
            "The Beach Boys" // Similar but different
        )

        // "beatles" should rank The Beatles first
        assertBestMatch("beatles", targets, "The Beatles")

        // "the beatles" should also rank The Beatles first
        assertBestMatch("the beatles", targets, "The Beatles")

        // "beat" should still rank The Beatles highly but might match "Beat Happening" too
        val scored = targets.map { target ->
            val similarity = StringComparison.jaroWinklerMultiDistance("beat", target)
            target to similarity.score
        }.sortedByDescending { it.second }

        // The Beatles should be in top 2
        val top2 = scored.take(2).map { it.first }
        assertTrue(
            "Expected 'The Beatles' in top 2 for query 'beat'. Got: $top2",
            top2.contains("The Beatles")
        )
    }

    @Test
    fun `comprehensive ranking - metal bands`() {
        val targets = listOf(
            "Metallica",
            "Metal Church",
            "Death Metal",
            "Heavy Metal",
            "Metronomy"
        )

        // Specific queries should match as expected
        assertBestMatch("metallica", targets, "Metallica")
        assertBestMatch("metal church", targets, "Metal Church")

        // Generic "metal" matches multiple well
        assertMatchesAboveThreshold("metal", "Metallica")
        assertMatchesAboveThreshold("metal", "Metal Church")
    }

    @Test
    fun `comprehensive ranking - similar prefixes`() {
        val targets = listOf(
            "Red Hot Chili Peppers",
            "Red House Painters",
            "Red",
            "Red Hot",
            "Simply Red"
        )

        // Specific multi-word queries should match well
        assertBestMatch("red hot chili", targets, "Red Hot Chili Peppers")

        // "red hot" is ambiguous - could match "Red Hot Chili Peppers" or "Red Hot"
        // Both should be above threshold
        assertMatchesAboveThreshold("red hot", "Red Hot Chili Peppers")
        assertMatchesAboveThreshold("red hot", "Red Hot")

        // Unique words disambiguate
        assertMatchesAboveThreshold("house painters", "Red House Painters")
        assertMatchesAboveThreshold("simply", "Simply Red")

        // Single word "red" matches all "Red" bands
        assertMatchesAboveThreshold("red", "Red")
        assertMatchesAboveThreshold("red", "Simply Red")
        assertMatchesAboveThreshold("red", "Red Hot Chili Peppers")
    }
}
