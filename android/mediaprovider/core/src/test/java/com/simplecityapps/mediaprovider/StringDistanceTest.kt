package com.simplecityapps.mediaprovider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StringDistanceTest {

    @Test
    fun `levenshteinDistance - exact match returns 0`() {
        assertEquals(0, StringDistance.levenshteinDistance("beatles", "beatles"))
        assertEquals(0, StringDistance.levenshteinDistance("", ""))
    }

    @Test
    fun `levenshteinDistance - case insensitive`() {
        assertEquals(0, StringDistance.levenshteinDistance("Beatles", "beatles"))
        assertEquals(0, StringDistance.levenshteinDistance("BEATLES", "beatles"))
    }

    @Test
    fun `levenshteinDistance - single character edits`() {
        // Insertion
        assertEquals(1, StringDistance.levenshteinDistance("beatles", "beatless"))

        // Deletion
        assertEquals(1, StringDistance.levenshteinDistance("beatles", "beatls"))

        // Substitution
        assertEquals(1, StringDistance.levenshteinDistance("beatles", "beazles"))
    }

    @Test
    fun `levenshteinDistance - common typos`() {
        // Transposed letters
        assertEquals(2, StringDistance.levenshteinDistance("beatles", "beatels"))

        // Missing letter
        assertEquals(1, StringDistance.levenshteinDistance("zeppelin", "zepplin"))

        // Wrong letter
        assertEquals(1, StringDistance.levenshteinDistance("nirvana", "nirvama"))
    }

    @Test
    fun `levenshteinDistance - early termination with maxDistance`() {
        // Should return MAX_VALUE if distance > maxDistance
        val result = StringDistance.levenshteinDistance("beatles", "stones", maxDistance = 2)
        assertEquals(Int.MAX_VALUE, result)
    }

    @Test
    fun `levenshteinDistance - length difference early termination`() {
        // Length difference of 5 > maxDistance of 2
        val result = StringDistance.levenshteinDistance("a", "abcdef", maxDistance = 2)
        assertEquals(Int.MAX_VALUE, result)
    }

    @Test
    fun `fuzzyMatches - accepts typos within tolerance`() {
        assertTrue(StringDistance.fuzzyMatches("beatles", "beatels", maxEdits = 2))
        assertTrue(StringDistance.fuzzyMatches("zeppelin", "zepplin", maxEdits = 2))
        assertTrue(StringDistance.fuzzyMatches("nirvana", "nirvama", maxEdits = 2))
    }

    @Test
    fun `fuzzyMatches - rejects typos outside tolerance`() {
        assertFalse(StringDistance.fuzzyMatches("beatles", "stones", maxEdits = 2))
        assertFalse(StringDistance.fuzzyMatches("beatles", "metal", maxEdits = 2))
    }

    @Test
    fun `similarity - exact match returns 1_0`() {
        assertEquals(1.0, StringDistance.similarity("beatles", "beatles"), 0.001)
        assertEquals(1.0, StringDistance.similarity("", ""), 0.001)
    }

    @Test
    fun `similarity - normalized score for partial matches`() {
        // "beatles" vs "beatels" = 2 edits / 7 length = 0.714...
        val score = StringDistance.similarity("beatles", "beatels")
        assertTrue("Score should be ~0.71", score > 0.70 && score < 0.75)
    }

    @Test
    fun `similarity - completely different strings return low score`() {
        val score = StringDistance.similarity("beatles", "xyz")
        assertTrue("Score should be very low", score < 0.30)
    }

    @Test
    fun `real-world scenario - music search typo tolerance`() {
        val queries = listOf(
            "beatels" to "beatles", // User types "beatels"
            "zepplin" to "led zeppelin", // Missing 'e'
            "pink floid" to "pink floyd", // Wrong letter
            "led zepelin" to "led zeppelin" // Missing 'p'
        )

        queries.forEach { (query, target) ->
            val distance = StringDistance.levenshteinDistance(query, target, maxDistance = 3)
            assertTrue(
                "Query '$query' should fuzzy-match '$target' (distance: $distance)",
                distance <= 2
            )
        }
    }

    @Test
    fun `performance - handles empty strings gracefully`() {
        assertEquals(5, StringDistance.levenshteinDistance("", "hello"))
        assertEquals(5, StringDistance.levenshteinDistance("hello", ""))
    }

    @Test
    fun `performance - early termination optimization works`() {
        // This should terminate early due to length difference
        val start = System.nanoTime()
        val result = StringDistance.levenshteinDistance(
            "a".repeat(100),
            "b".repeat(1000),
            maxDistance = 2
        )
        val duration = System.nanoTime() - start

        assertEquals(Int.MAX_VALUE, result)
        // Should be very fast due to early termination
        assertTrue("Should terminate quickly", duration < 1_000_000) // < 1ms
    }

    @Test
    fun `comparison with Jaro-Winkler for typos`() {
        // Levenshtein is better for typo detection than Jaro-Winkler
        val typo1 = StringDistance.levenshteinDistance("beatles", "beatels")
        val typo2 = StringDistance.levenshteinDistance("zeppelin", "zepplin")

        // Both should be detected as 1-2 character typos
        assertTrue(typo1 <= 2)
        assertTrue(typo2 <= 2)

        // Jaro-Winkler would give these high scores but wouldn't
        // tell us exactly how many edits are needed
    }
}
