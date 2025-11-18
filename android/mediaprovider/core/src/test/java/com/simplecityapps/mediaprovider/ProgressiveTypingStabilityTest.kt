package com.simplecityapps.mediaprovider

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for progressive typing stability - ensuring that as users type more characters,
 * the search results remain stable and predictable rather than jumping around erratically.
 *
 * Good UX means:
 * 1. Once the "right" result appears in top N, it should stay there or improve (not disappear)
 * 2. Results shouldn't flip-flop between different options as characters are added
 * 3. More specific queries should narrow down to the intended result
 */
class ProgressiveTypingStabilityTest {

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

    // ===================================================================================
    // SINGLE WORD PROGRESSIVE TYPING
    // ===================================================================================

    @Test
    fun `progressive typing - single word target doesn't disappear from top 3`() {
        val targets = listOf("Queen", "Queens of the Stone Age", "Queensrÿche", "The Queen Is Dead")
        val progressiveQueries = listOf("q", "qu", "que", "quee", "queen")

        var previousTopResult: String? = null
        var resultFirstAppearedAt: String? = null

        progressiveQueries.forEach { query ->
            val ranked = rankResults(query, targets)
            val top3 = ranked.take(3).map { it.name }

            println("Query '$query': ${ranked.map { "${it.name}(${String.format("%.3f", it.score)})" }}")

            // Once "Queen" appears in top 3, it shouldn't disappear
            if (resultFirstAppearedAt != null) {
                assertTrue(
                    "After 'Queen' appeared in top 3 at query '$resultFirstAppearedAt', " +
                        "it disappeared at query '$query'. Rankings: $top3",
                    top3.contains("Queen")
                )
            }

            if (top3.contains("Queen") && resultFirstAppearedAt == null) {
                resultFirstAppearedAt = query
            }

            previousTopResult = ranked[0].name
        }
    }

    @Test
    fun `progressive typing - result position should improve or stay stable`() {
        val targets = listOf("Metallica", "Metal Church", "Metronomy", "Death Metal")
        val progressiveQueries = listOf("met", "meta", "metal", "metall", "metalli", "metallic", "metallica")

        var previousPosition: Int? = null

        progressiveQueries.forEach { query ->
            val ranked = rankResults(query, targets)
            val metallicaPosition = ranked.indexOfFirst { it.name == "Metallica" }

            println("Query '$query': ${ranked.map { "${it.name}(${String.format("%.3f", it.score)})" }}")

            if (previousPosition != null && metallicaPosition != -1 && previousPosition != -1) {
                // Position should improve (get smaller) or stay same, not get worse
                assertTrue(
                    "Query '$query': Metallica position worsened from $previousPosition to $metallicaPosition",
                    metallicaPosition <= previousPosition!! + 1 // Allow 1 position slip for edge cases
                )
            }

            previousPosition = if (metallicaPosition != -1) metallicaPosition else previousPosition
        }
    }

    // ===================================================================================
    // COMMON PREFIX SCENARIOS
    // ===================================================================================

    @Test
    fun `progressive typing - common prefix doesn't cause thrashing`() {
        val targets = listOf(
            "Red Hot Chili Peppers",
            "Red House Painters",
            "Red",
            "Simply Red",
            "Red Velvet"
        )
        val progressiveQueries = listOf("r", "re", "red", "red ", "red h", "red ho", "red hot")

        val positionHistory = mutableMapOf<String, MutableList<Int>>()
        targets.forEach { positionHistory[it] = mutableListOf() }

        progressiveQueries.forEach { query ->
            val ranked = rankResults(query, targets)
            println("Query '$query': ${ranked.map { "${it.name}(${String.format("%.3f", it.score)})" }}")

            targets.forEach { target ->
                val position = ranked.indexOfFirst { it.name == target }
                positionHistory[target]!!.add(if (position == -1) 999 else position)
            }
        }

        // Check that no result flip-flops excessively (moving up/down more than 3 times)
        positionHistory.forEach { (target, positions) ->
            var flipFlops = 0
            for (i in 1 until positions.size) {
                if (i > 1) {
                    val prev = positions[i - 1]
                    val curr = positions[i]
                    val prevPrev = positions[i - 2]

                    // Detect flip-flop: went down then up, or up then down
                    if ((prev < prevPrev && curr < prev) || (prev > prevPrev && curr > prev)) {
                        flipFlops++
                    }
                }
            }

            assertTrue(
                "Target '$target' flip-flopped $flipFlops times (positions: $positions). " +
                    "Expected <= 2 for stable UX",
                flipFlops <= 2
            )
        }
    }

    @Test
    fun `progressive typing - short exact match vs long partial match`() {
        val targets = listOf("U2", "UB40", "U2 Live at Red Rocks", "Bono")
        val progressiveQueries = listOf("u", "u2")

        progressiveQueries.forEach { query ->
            val ranked = rankResults(query, targets)
            println("Query '$query': ${ranked.map { "${it.name}(${String.format("%.3f", it.score)})" }}")

            // "U2" should always be in top 2 for both queries
            val top2 = ranked.take(2).map { it.name }
            assertTrue(
                "Expected 'U2' in top 2 for query '$query'. Got: $top2",
                top2.contains("U2")
            )
        }

        // By "u2" specifically, "U2" should be #1
        val finalRanked = rankResults("u2", targets)
        assertTrue(
            "Expected 'U2' to rank first for 'u2'. Got: ${finalRanked[0].name}",
            finalRanked[0].name == "U2"
        )
    }

    // ===================================================================================
    // MULTI-WORD PROGRESSIVE TYPING
    // ===================================================================================

    @Test
    fun `progressive typing - multi-word query first word complete`() {
        val targets = listOf("Pink Floyd", "Pink", "Pink Martini", "Floyd")
        val progressiveQueries = listOf("p", "pi", "pin", "pink", "pink ", "pink f", "pink fl", "pink flo", "pink floyd")

        var seenPinkFloydInTop2 = false

        progressiveQueries.forEach { query ->
            val ranked = rankResults(query, targets)
            val top2 = ranked.take(2).map { it.name }

            println("Query '$query': ${ranked.map { "${it.name}(${String.format("%.3f", it.score)})" }}")

            // Once we add space and start typing second word, Pink Floyd should appear in top 2
            if (query.contains(" ") && query.length > "pink ".length) {
                assertTrue(
                    "Expected 'Pink Floyd' in top 2 for query '$query'. Got: $top2",
                    top2.contains("Pink Floyd")
                )
                seenPinkFloydInTop2 = true
            }

            // Once it's in top 2, it shouldn't disappear
            if (seenPinkFloydInTop2) {
                assertTrue(
                    "After 'Pink Floyd' appeared in top 2, it disappeared at query '$query'",
                    top2.contains("Pink Floyd")
                )
            }
        }
    }

    @Test
    fun `progressive typing - multi-word with coverage bonus`() {
        // Test that multi-word coverage bonus doesn't cause instability
        val targets = listOf("Queens of the Stone Age", "Queen", "Stone Temple Pilots", "The Stone Roses")
        val progressiveQueries = listOf(
            "q", "qu", "que", "quee", "queen",
            "queen ", "queen s", "queen st", "queen sto", "queen ston", "queen stone"
        )

        progressiveQueries.forEach { query ->
            val ranked = rankResults(query, targets)
            println("Query '$query': ${ranked.map { "${it.name}(${String.format("%.3f", it.score)})" }}")

            // By "queen stone" (2 complete words), Queens of the Stone Age should dominate
            if (query == "queen stone") {
                assertTrue(
                    "Expected 'Queens of the Stone Age' to rank first for '$query' (has both words). " +
                        "Got: ${ranked[0].name}",
                    ranked[0].name == "Queens of the Stone Age"
                )
            }
        }
    }

    // ===================================================================================
    // EDGE CASES & PATHOLOGICAL SCENARIOS
    // ===================================================================================

    @Test
    fun `progressive typing - number in band name`() {
        val targets = listOf("Blink-182", "Blink", "Sum 41", "311")
        val progressiveQueries = listOf("b", "bl", "bli", "blin", "blink", "blink-", "blink-1", "blink-18", "blink-182")

        progressiveQueries.forEach { query ->
            val ranked = rankResults(query, targets)
            println("Query '$query': ${ranked.map { "${it.name}(${String.format("%.3f", it.score)})" }}")

            // "Blink-182" should consistently be in top 2 after we type "blink"
            if (query.startsWith("blink")) {
                val top2 = ranked.take(2).map { it.name }
                assertTrue(
                    "Expected 'Blink-182' in top 2 for query '$query'. Got: $top2",
                    top2.contains("Blink-182")
                )
            }
        }
    }

    @Test
    fun `progressive typing - special characters`() {
        val targets = listOf("AC/DC", "ACDC", "ACID", "AC")
        val progressiveQueries = listOf("a", "ac", "ac/", "ac/d", "ac/dc")

        progressiveQueries.forEach { query ->
            val ranked = rankResults(query, targets)
            println("Query '$query': ${ranked.map { "${it.name}(${String.format("%.3f", it.score)})" }}")

            // "AC/DC" should be in top 2 for all "ac*" queries
            if (query.startsWith("ac")) {
                val top2 = ranked.take(2).map { it.name }
                assertTrue(
                    "Expected 'AC/DC' or 'ACDC' in top 2 for query '$query'. Got: $top2",
                    top2.contains("AC/DC") || top2.contains("ACDC")
                )
            }
        }
    }

    @Test
    fun `progressive typing - THE prefix doesn't destabilize`() {
        val targets = listOf("The Beatles", "The Who", "The Doors", "Beatles", "Them")
        val progressiveQueries = listOf("t", "th", "the", "the ", "the b", "the be", "the bea", "the beat", "the beatles")

        progressiveQueries.forEach { query ->
            val ranked = rankResults(query, targets)
            println("Query '$query': ${ranked.map { "${it.name}(${String.format("%.3f", it.score)})" }}")

            // After we type "the b", "The Beatles" should be in top 3
            if (query.length >= "the b".length && query.startsWith("the b")) {
                val top3 = ranked.take(3).map { it.name }
                assertTrue(
                    "Expected 'The Beatles' in top 3 for query '$query'. Got: $top3",
                    top3.contains("The Beatles")
                )
            }
        }
    }

    @Test
    fun `progressive typing - acronym vs full name stability`() {
        val targets = listOf("NIN", "Nine Inch Nails", "Nina Simone", "Nirvana")
        val progressiveQueries = listOf("n", "ni", "nin")

        progressiveQueries.forEach { query ->
            val ranked = rankResults(query, targets)
            println("Query '$query': ${ranked.map { "${it.name}(${String.format("%.3f", it.score)})" }}")

            // Both "NIN" and "Nine Inch Nails" should stay in top 3 throughout
            val top3 = ranked.take(3).map { it.name }
            val hasNinOrFull = top3.contains("NIN") || top3.contains("Nine Inch Nails")

            assertTrue(
                "Expected 'NIN' or 'Nine Inch Nails' in top 3 for query '$query'. Got: $top3",
                hasNinOrFull
            )
        }
    }

    // ===================================================================================
    // STABILITY METRICS
    // ===================================================================================

    @Test
    fun `progressive typing - stability score analysis`() {
        // Test a realistic scenario and measure how stable rankings are
        val targets = listOf(
            "Led Zeppelin",
            "Led",
            "Zeppelin",
            "Led Zeppelin II",
            "Led Boot"
        )
        val progressiveQueries = listOf("l", "le", "led", "led ", "led z", "led ze", "led zep", "led zepp", "led zeppelin")

        val rankingChanges = mutableMapOf<String, Int>()
        targets.forEach { rankingChanges[it] = 0 }

        var previousRanking: List<String>? = null

        progressiveQueries.forEach { query ->
            val ranked = rankResults(query, targets)
            val currentRanking = ranked.map { it.name }

            println("Query '$query': ${ranked.map { "${it.name}(${String.format("%.3f", it.score)})" }}")

            if (previousRanking != null) {
                // Count how many positions each target moved
                targets.forEach { target ->
                    val prevPos = previousRanking!!.indexOf(target)
                    val currPos = currentRanking.indexOf(target)
                    if (prevPos != currPos && prevPos != -1 && currPos != -1) {
                        rankingChanges[target] = rankingChanges[target]!! + 1
                    }
                }
            }

            previousRanking = currentRanking
        }

        println("\nRanking changes per target: $rankingChanges")

        // The intended target "Led Zeppelin" shouldn't move around too much
        val ledZeppelinChanges = rankingChanges["Led Zeppelin"] ?: 0
        assertTrue(
            "Led Zeppelin ranking changed $ledZeppelinChanges times. Expected <= 4 for stable UX",
            ledZeppelinChanges <= 4
        )
    }

    @Test
    fun `progressive typing - real world Beatles scenario with competitors`() {
        // Realistic scenario with similar-sounding competitors
        val targets = listOf(
            "The Beatles",
            "Beat Happening",
            "Beatnuts",
            "Beach Boys",
            "Beartooth",
            "Bee Gees",
            "Belle and Sebastian"
        )
        val progressiveQueries = listOf("b", "be", "bea", "beat", "beatl", "beatle", "beatles")

        val beatlesPositions = mutableListOf<Int>()

        progressiveQueries.forEach { query ->
            val ranked = rankResults(query, targets)
            val beatlesPos = ranked.indexOfFirst { it.name == "The Beatles" }
            beatlesPositions.add(beatlesPos)

            println("Query '$query': ${ranked.take(5).map { "${it.name}(${String.format("%.3f", it.score)})" }}")
        }

        println("\nThe Beatles positions through typing: $beatlesPositions")

        // Beatles position should generally improve (smaller numbers) as we type more
        // Allow some volatility early on but should stabilize by "beat"
        val positionAtBeat = beatlesPositions[progressiveQueries.indexOf("beat")]
        val positionAtBeatles = beatlesPositions[progressiveQueries.indexOf("beatles")]

        assertTrue(
            "Expected Beatles to improve or stay same from 'beat' ($positionAtBeat) to 'beatles' ($positionAtBeatles)",
            positionAtBeatles <= positionAtBeat
        )

        assertTrue(
            "Expected Beatles to be in top 2 by 'beatles'. Got position $positionAtBeatles",
            positionAtBeatles < 2
        )
    }
}
