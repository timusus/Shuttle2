package com.simplecityapps.mediaprovider

import org.junit.Assert.*
import org.junit.Test

class StringComparisonTest {

    @Test
    fun `jaroWinklerDistance - exact match returns score of 1_0`() {
        val result = StringComparison.jaroWinklerDistance("beatles", "beatles")
        assertEquals(1.0, result.score, 0.001)
    }

    @Test
    fun `jaroWinklerDistance - case insensitive matching`() {
        val result = StringComparison.jaroWinklerDistance("Beatles", "beatles")
        assertEquals(1.0, result.score, 0.001)
    }

    @Test
    fun `jaroWinklerDistance - handles unicode normalization`() {
        val result = StringComparison.jaroWinklerDistance("café", "cafe")
        // Should have a high score due to normalization
        assertTrue(result.score > 0.90)
    }

    @Test
    fun `jaroWinklerDistance - prefix matching gets bonus`() {
        val withPrefix = StringComparison.jaroWinklerDistance("abc", "abcdefg")
        val withoutPrefix = StringComparison.jaroWinklerDistance("efg", "abcdefg")

        // Prefix match should score higher due to Winkler modification
        assertTrue(withPrefix.score > withoutPrefix.score)
    }

    @Test
    fun `jaroWinklerMultiDistance - matches full string when above threshold`() {
        val result = StringComparison.jaroWinklerMultiDistance("beatles", "beatles")
        assertEquals(1.0, result.score, 0.001)
    }

    @Test
    fun `jaroWinklerMultiDistance - matches individual words in target`() {
        // "beatles" should match "beatles" in "the beatles"
        val result = StringComparison.jaroWinklerMultiDistance("beatles", "the beatles")
        assertEquals(1.0, result.score, 0.001)
    }

    @Test
    fun `jaroWinklerMultiDistance - handles led zeppelin substring query`() {
        // "zeppelin" should match "zeppelin" in "led zeppelin"
        val result = StringComparison.jaroWinklerMultiDistance("zeppelin", "led zeppelin")
        assertEquals(1.0, result.score, 0.001)
    }

    @Test
    fun `jaroWinklerMultiDistance - handles multi-word query against multi-word target`() {
        // "dark side" should match well against "the dark side of the moon"
        val result = StringComparison.jaroWinklerMultiDistance("dark side", "the dark side of the moon")
        // Should get a high score by matching "dark" or "side" individually
        assertTrue(result.score > 0.85)
    }

    @Test
    fun `jaroWinklerMultiDistance - multi-word query matches individual target words`() {
        // "side moon" against "the dark side of the moon" should match "side" or "moon"
        val result = StringComparison.jaroWinklerMultiDistance("side moon", "the dark side of the moon")
        assertEquals(1.0, result.score, 0.001) // "moon" should be exact match
    }

    @Test
    fun `threshold constant is appropriate for music search`() {
        // The threshold of 0.85 should be permissive enough for common searches
        assertEquals(0.85, StringComparison.threshold, 0.001)

        // Test that common searches pass the threshold
        val beatlesMatch = StringComparison.jaroWinklerMultiDistance("beatles", "the beatles")
        assertTrue(beatlesMatch.score > StringComparison.threshold)

        val zeppelinMatch = StringComparison.jaroWinklerMultiDistance("zeppelin", "led zeppelin")
        assertTrue(zeppelinMatch.score > StringComparison.threshold)
    }

    @Test
    fun `real-world scenario - searching for artist by partial name`() {
        val queries = listOf(
            "beatles" to "The Beatles",
            "zeppelin" to "Led Zeppelin",
            "pink floyd" to "Pink Floyd",
            "stones" to "The Rolling Stones",
            "nirvana" to "Nirvana"
        )

        queries.forEach { (query, target) ->
            val result = StringComparison.jaroWinklerMultiDistance(query, target)
            assertTrue(
                "Query '$query' should match '$target' with score > threshold",
                result.score > StringComparison.threshold
            )
        }
    }

    @Test
    fun `real-world scenario - searching for album with partial title`() {
        val queries = listOf(
            "dark side" to "The Dark Side of the Moon",
            "abbey road" to "Abbey Road",
            "sgt pepper" to "Sgt. Pepper's Lonely Hearts Club Band",
            "back in black" to "Back in Black"
        )

        queries.forEach { (query, target) ->
            val result = StringComparison.jaroWinklerMultiDistance(query, target)
            assertTrue(
                "Query '$query' should match '$target' with score > threshold",
                result.score > StringComparison.threshold
            )
        }
    }

    @Test
    fun `handles typos with reasonable tolerance`() {
        val typos = listOf(
            "beatels" to "beatles",    // common typo
            "zepplin" to "zeppelin",    // common misspelling
            "niravna" to "nirvana"      // transposed letters
        )

        typos.forEach { (query, target) ->
            val result = StringComparison.jaroWinklerDistance(query, target)
            assertTrue(
                "Typo '$query' should reasonably match '$target'",
                result.score > 0.80
            )
        }
    }

    @Test
    fun `matched indices are correctly tracked for highlighting`() {
        val result = StringComparison.jaroWinklerDistance("test", "test")

        // All characters should be matched
        assertEquals(4, result.aMatchedIndices.size)
        assertEquals(4, result.bMatchedIndices.size)

        // All matches should have score of 1.0 for exact match
        result.aMatchedIndices.values.forEach { score ->
            assertEquals(1.0, score, 0.001)
        }
    }

    @Test
    fun `matched indices for multi-word matching are correctly offset`() {
        val result = StringComparison.jaroWinklerMultiDistance("beatles", "the beatles")

        // Should match the second word "beatles" in "the beatles"
        // The matched indices in bMatchedIndices should be offset by "the ".length = 4
        assertTrue(result.bMatchedIndices.keys.any { it >= 4 })
    }

    @Test
    fun `empty query returns zero score`() {
        val result = StringComparison.jaroWinklerDistance("", "something")
        assertEquals(0.0, result.score, 0.001)
    }

    @Test
    fun `empty target returns zero score`() {
        val result = StringComparison.jaroWinklerDistance("something", "")
        assertEquals(0.0, result.score, 0.001)
    }

    @Test
    fun `completely different strings return low score`() {
        val result = StringComparison.jaroWinklerDistance("abcdef", "xyz123")
        assertTrue(result.score < 0.50)
    }

    @Test
    fun `custom threshold in multiDistance affects word splitting behavior`() {
        // With a very high threshold, should try word splitting more aggressively
        val result = StringComparison.jaroWinklerMultiDistance(
            "beat",
            "the beatles",
            multiWordThreshold = 0.99
        )

        // Should match "beat" part of "beatles" in the second word
        assertTrue(result.score > 0.80)
    }

    @Test
    fun `short query against long target handles edge cases`() {
        val result = StringComparison.jaroWinklerMultiDistance("a", "a very long target string")
        assertEquals(1.0, result.score, 0.001) // Should match "a"
    }

    @Test
    fun `transpositions are penalized but not rejected`() {
        val result = StringComparison.jaroWinklerDistance("abcd", "abdc")

        // Should have high but not perfect score due to transposition
        assertTrue(result.score > 0.85)
        assertTrue(result.score < 1.0)
    }

    @Test
    fun `prefix bonus increases score significantly`() {
        // Compare Jaro vs Jaro-Winkler for prefix matching
        val jaroResult = StringComparison.jaroDistance("prefix", "prefixtest")
        val jaroWinklerResult = StringComparison.jaroWinklerDistance("prefix", "prefixtest")

        // Jaro-Winkler should score higher due to matching prefix
        assertTrue(jaroWinklerResult.score > jaroResult.score)
    }

    @Test
    fun `multi-word query tokens are independently matched`() {
        // When query has multiple words, each word should be tried against target
        val result = StringComparison.jaroWinklerMultiDistance(
            "help abbey",
            "Abbey Road"
        )

        // "abbey" should match "Abbey" with high score
        assertTrue(result.score > 0.95)
    }

    @Test
    fun `matching is symmetric for single words`() {
        val result1 = StringComparison.jaroWinklerDistance("beatles", "stones")
        val result2 = StringComparison.jaroWinklerDistance("stones", "beatles")

        // Scores should be identical when matching single words
        assertEquals(result1.score, result2.score, 0.001)
    }
}
