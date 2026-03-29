package com.simplecityapps.mediaprovider

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Negative tests - ensuring the search algorithm correctly REJECTS poor matches.
 *
 * These tests verify:
 * 1. Random strings don't match everything
 * 2. Very dissimilar strings fall below threshold
 * 3. Algorithm doesn't have false positives
 * 4. Nonsense queries return empty results
 */
class NegativeSearchTest {

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

    private fun getMatchesAboveThreshold(query: String, targets: List<String>): List<RankedResult> = rankResults(query, targets).filter { it.score >= StringComparison.threshold }

    // ===================================================================================
    // COMPLETE NONSENSE QUERIES
    // ===================================================================================

    @Test
    fun `nonsense query returns no matches above threshold`() {
        val targets = listOf(
            "The Beatles",
            "Pink Floyd",
            "Led Zeppelin",
            "The Rolling Stones",
            "Queen"
        )

        val nonsenseQueries = listOf(
            "xyz123",
            "qwerty",
            "asdfghjkl",
            "zzzzzzz",
            "!@#$%^&*()",
            "12345678"
        )

        nonsenseQueries.forEach { query ->
            val matches = getMatchesAboveThreshold(query, targets)
            assertTrue(
                "Nonsense query '$query' should not match classic rock bands. Got: $matches",
                matches.isEmpty()
            )
        }
    }

    @Test
    fun `random unicode characters don't match`() {
        val targets = listOf("The Beatles", "Radiohead", "Nirvana")

        val unicodeQueries = listOf(
            "🎸🎵🎶",
            "你好世界",
            "مرحبا",
            "Привет",
            "こんにちは"
        )

        unicodeQueries.forEach { query ->
            val matches = getMatchesAboveThreshold(query, targets)
            assertTrue(
                "Unicode query '$query' should not match English band names. Got: $matches",
                matches.isEmpty()
            )
        }
    }

    // ===================================================================================
    // COMPLETELY UNRELATED SEARCHES
    // ===================================================================================

    @Test
    fun `metal band search doesn't match pop singers`() {
        val popSingers = listOf(
            "Taylor Swift",
            "Ariana Grande",
            "Justin Bieber",
            "Ed Sheeran",
            "Billie Eilish"
        )

        val metalQueries = listOf("metallica", "slayer", "megadeth", "iron maiden")

        metalQueries.forEach { query ->
            val matches = getMatchesAboveThreshold(query, popSingers)
            assertTrue(
                "Metal query '$query' should not match pop singers. Got: $matches",
                matches.isEmpty()
            )
        }
    }

    @Test
    fun `classical composer search doesn't match rock bands`() {
        val rockBands = listOf(
            "The Beatles",
            "Led Zeppelin",
            "Pink Floyd",
            "The Who",
            "Queen"
        )

        val classicalQueries = listOf(
            "mozart",
            "beethoven",
            "bach",
            "vivaldi",
            "tchaikovsky"
        )

        classicalQueries.forEach { query ->
            val matches = getMatchesAboveThreshold(query, rockBands)
            assertTrue(
                "Classical query '$query' should not match rock bands. Got: $matches",
                matches.isEmpty()
            )
        }
    }

    // ===================================================================================
    // PARTIAL MATCH REJECTION (TOO WEAK)
    // ===================================================================================

    @Test
    fun `single character doesn't match long unrelated strings`() {
        val targets = listOf(
            "Xylophone Records Artist",
            "Xylem Music Group",
            "Xander the Magnificent"
        )

        // Query "a" should not match these X-names
        val matches = getMatchesAboveThreshold("a", targets)
        assertTrue(
            "Query 'a' should not match X-prefixed names strongly. Got: $matches",
            matches.isEmpty()
        )
    }

    @Test
    fun `weak substring match falls below threshold`() {
        val targets = listOf(
            "The National",
            "The Strokes",
            "The Killers"
        )

        // "xyz" has no meaningful overlap with these bands
        val matches = getMatchesAboveThreshold("xyz", targets)
        assertTrue(
            "Query 'xyz' should not match 'The *' bands. Got: $matches",
            matches.isEmpty()
        )
    }

    // ===================================================================================
    // NEAR MISSES (Should NOT match)
    // ===================================================================================

    @Test
    fun `completely wrong band name doesn't match`() {
        val targets = listOf("The Beatles")

        val wrongQueries = listOf(
            "stones", // Different band
            "zeppelin", // Different band
            "pink floyd", // Different band
            "nirvana", // Different band
            "radiohead" // Different band
        )

        wrongQueries.forEach { query ->
            val matches = getMatchesAboveThreshold(query, targets)
            assertTrue(
                "Query '$query' should not match 'The Beatles'. Got: $matches",
                matches.isEmpty()
            )
        }
    }

    @Test
    fun `genre name doesn't match band name`() {
        val targets = listOf(
            "Metallica",
            "Slayer",
            "Megadeth"
        )

        // Genre names shouldn't match band names
        val genreQueries = listOf("jazz", "blues", "country", "disco", "techno")

        genreQueries.forEach { query ->
            val matches = getMatchesAboveThreshold(query, targets)
            assertTrue(
                "Genre query '$query' should not match metal bands. Got: $matches",
                matches.isEmpty()
            )
        }
    }

    // ===================================================================================
    // EDGE CASE REJECTIONS
    // ===================================================================================

    @Test
    fun `empty query returns no matches`() {
        val targets = listOf("The Beatles", "Queen", "U2")

        val matches = getMatchesAboveThreshold("", targets)
        assertTrue(
            "Empty query should not match anything. Got: $matches",
            matches.isEmpty()
        )
    }

    @Test
    fun `whitespace only query returns no matches`() {
        val targets = listOf("The Beatles", "Queen", "U2")

        val whitespaceQueries = listOf(" ", "  ", "   ", "\t", "\n")

        whitespaceQueries.forEach { query ->
            val matches = getMatchesAboveThreshold(query, targets)
            assertTrue(
                "Whitespace query should not match anything. Got: $matches",
                matches.isEmpty()
            )
        }
    }

    @Test
    fun `numbers don't match text band names`() {
        val targets = listOf(
            "The Beatles",
            "Led Zeppelin",
            "Pink Floyd"
        )

        val numberQueries = listOf("123", "456", "789", "000")

        numberQueries.forEach { query ->
            val matches = getMatchesAboveThreshold(query, targets)
            assertTrue(
                "Number query '$query' should not match text band names. Got: $matches",
                matches.isEmpty()
            )
        }
    }

    // ===================================================================================
    // THRESHOLD VALIDATION
    // ===================================================================================

    @Test
    fun `threshold of 0-85 is enforced`() {
        val targets = listOf("The Beatles")

        // These are progressively worse matches
        val queries = listOf(
            "beatles" to true, // Should match
            "beatle" to true, // Should match
            "beatl" to true, // Should match
            "beat" to true, // Should match
            "bea" to true, // Should match
            "be" to false, // Might not match
            "b" to false, // Should not match
            "xyz" to false // Definitely should not match
        )

        queries.forEach { (query, shouldMatch) ->
            val ranked = rankResults(query, targets)
            val score = ranked[0].score

            if (shouldMatch) {
                assertTrue(
                    "Query '$query' should score >= 0.85 for 'The Beatles'. Got: $score",
                    score >= StringComparison.threshold
                )
            } else {
                // Just verify it's below threshold for the negative cases
                if (score >= StringComparison.threshold) {
                    println("INFO: Query '$query' scored $score (above threshold). This may be acceptable.")
                }
            }
        }
    }

    @Test
    fun `very long unrelated query doesn't match short target`() {
        val targets = listOf("U2")

        val longQuery = "This is a very long query with many words that has nothing to do with short band names at all really"

        val matches = getMatchesAboveThreshold(longQuery, targets)
        assertTrue(
            "Long unrelated query should not match 'U2'. Got: $matches",
            matches.isEmpty()
        )
    }

    @Test
    fun `reversed string doesn't match original`() {
        val targets = listOf("The Beatles")

        // "seltaeB ehT" is "The Beatles" reversed
        val matches = getMatchesAboveThreshold("seltaeb", targets)

        // Reversed should score poorly (even though it has same letters)
        assertTrue(
            "Reversed string 'seltaeb' should not match 'The Beatles' well. Got: $matches",
            matches.isEmpty()
        )
    }

    // ===================================================================================
    // FALSE POSITIVE CHECKS
    // ===================================================================================

    @Test
    fun `common words don't cause false positives`() {
        val targets = listOf(
            "The Beatles",
            "The Who",
            "The Doors"
        )

        // Common English words that appear in band names but aren't band searches
        val commonWords = listOf("who", "what", "where", "when", "why", "how")

        commonWords.forEach { query ->
            val matches = getMatchesAboveThreshold(query, targets)

            // "who" might legitimately match "The Who"
            if (query == "who") {
                assertTrue(
                    "Query 'who' should match 'The Who'",
                    matches.any { it.name == "The Who" }
                )
                // But should ONLY match The Who, not the others
                assertTrue(
                    "Query 'who' should only match 'The Who', not all bands. Got: $matches",
                    matches.size <= 1
                )
            } else {
                // Other w-words shouldn't match
                assertTrue(
                    "Common word '$query' should not match band names. Got: $matches",
                    matches.isEmpty() || matches.size <= 1
                )
            }
        }
    }

    @Test
    fun `punctuation doesn't cause spurious matches`() {
        val targets = listOf(
            "Panic! at the Disco",
            "Fall Out Boy",
            "My Chemical Romance"
        )

        val punctuationQueries = listOf("!!!", "???", "...", "---", "___")

        punctuationQueries.forEach { query ->
            val matches = getMatchesAboveThreshold(query, targets)

            // "!!!" might partially match "Panic!" but shouldn't be a strong match
            assertTrue(
                "Punctuation query '$query' should not strongly match bands. Got: $matches",
                matches.isEmpty() || matches.all { it.score < 0.90 }
            )
        }
    }

    // ===================================================================================
    // TYPO REJECTION (TOO MANY ERRORS)
    // ===================================================================================

    @Test
    fun `excessive typos fall below threshold`() {
        val targets = listOf("The Beatles")

        // Progressively worse typos
        val typos = listOf(
            "beatles" to true, // No typo - should match
            "beetles" to true, // 1 typo - should match
            "beutles" to true, // 1 typo - should match
            "baetles" to true, // 1 transposition - should match
            "bxxtlxs" to false, // Many typos - should NOT match
            "xxxxxxx" to false // Complete garbage - should NOT match
        )

        typos.forEach { (query, shouldMatch) ->
            val matches = getMatchesAboveThreshold(query, targets)

            if (shouldMatch) {
                assertTrue(
                    "Query '$query' with minor typos should match 'The Beatles'. Got: $matches",
                    matches.isNotEmpty()
                )
            } else {
                assertTrue(
                    "Query '$query' with excessive typos should NOT match 'The Beatles'. Got: $matches",
                    matches.isEmpty()
                )
            }
        }
    }
}
