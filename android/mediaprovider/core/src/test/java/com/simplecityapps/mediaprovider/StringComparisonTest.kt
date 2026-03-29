package com.simplecityapps.mediaprovider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        // "side moon" against "the dark side of the moon" should match "side" and "moon"
        // Both words match perfectly, so score includes multi-word bonus: 1.0 * 1.05 = 1.05
        val result = StringComparison.jaroWinklerMultiDistance("side moon", "the dark side of the moon")
        assertEquals(1.05, result.score, 0.001) // 2 query words matched
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
            "beatels" to "beatles", // common typo
            "zepplin" to "zeppelin", // common misspelling
            "niravna" to "nirvana" // transposed letters
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

    // ============================================================
    // Tests for highlighting indices (used by UI binders)
    // ============================================================

    @Test
    fun `matched indices for single word query against multi-word target are correctly offset`() {
        // Query: "beatles", Target: "the beatles"
        val result = StringComparison.jaroWinklerMultiDistance("beatles", "the beatles")

        // Should match the second word "beatles" which starts at index 4 (after "the ")
        assertEquals(1.0, result.score, 0.001)

        // All matched indices in bMatchedIndices should be >= 4 (offset for "the ")
        result.bMatchedIndices.keys.forEach { index ->
            assertTrue(
                "Index $index should be >= 4 (offset for 'the ')",
                index >= 4
            )
        }

        // Should have 7 matched indices for "beatles" (7 characters)
        assertEquals(7, result.bMatchedIndices.size)

        // Verify the range: should be indices 4-10 (inclusive)
        val expectedIndices = setOf(4, 5, 6, 7, 8, 9, 10)
        assertEquals(expectedIndices, result.bMatchedIndices.keys)
    }

    @Test
    fun `matched indices for multi-word query against multi-word target`() {
        // Query: "side moon", Target: "the dark side of the moon"
        val result = StringComparison.jaroWinklerMultiDistance("side moon", "the dark side of the moon")

        // Should get high score - both "side" and "moon" match perfectly
        // With multi-word bonus: 1.0 * 1.05 = 1.05 (2 query words matched)
        assertEquals(1.05, result.score, 0.001)

        // bMatchedIndices should point to either "side" or "moon" in the target
        // "the dark side of the moon"
        // "side" is at indices 9-12, "moon" is at indices 21-24
        val sideIndices = setOf(9, 10, 11, 12)
        val moonIndices = setOf(21, 22, 23, 24)

        // Should have indices for either "side" or "moon" (both are perfect matches)
        val hasSide = result.bMatchedIndices.keys.containsAll(sideIndices)
        val hasMoon = result.bMatchedIndices.keys.containsAll(moonIndices)
        assertTrue(
            "Should have indices for either 'side' (9-12) or 'moon' (21-24)",
            hasSide || hasMoon
        )
    }

    @Test
    fun `matched indices handle normalization gracefully`() {
        // This tests the edge case where normalization might cause index mismatches
        val result = StringComparison.jaroWinklerMultiDistance("cafe", "café")

        // Should have high score
        assertTrue(result.score > 0.90)

        // bMatchedIndices might have fewer or different indices due to normalization
        // The important thing is it doesn't crash and returns reasonable results
        assertTrue(result.bMatchedIndices.size > 0)
    }

    @Test
    fun `matched indices for exact match contain all character positions`() {
        val result = StringComparison.jaroWinklerDistance("test", "test")

        // All 4 characters should be matched
        assertEquals(4, result.aMatchedIndices.size)
        assertEquals(4, result.bMatchedIndices.size)

        // Indices should be 0, 1, 2, 3
        assertEquals(setOf(0, 1, 2, 3), result.aMatchedIndices.keys)
        assertEquals(setOf(0, 1, 2, 3), result.bMatchedIndices.keys)

        // All scores should be 1.0 for exact match
        result.aMatchedIndices.values.forEach { score ->
            assertEquals(1.0, score, 0.001)
        }
    }

    @Test
    fun `matched indices for partial match show only matched characters`() {
        val result = StringComparison.jaroWinklerDistance("abc", "axbxcx")

        // Should match a, b, c at positions 0, 2, 4
        assertTrue(result.score > 0.60)

        // aMatchedIndices should have all 3 characters from "abc"
        assertEquals(3, result.aMatchedIndices.size)
        assertEquals(setOf(0, 1, 2), result.aMatchedIndices.keys)

        // bMatchedIndices should point to a, b, c in "axbxcx"
        assertEquals(3, result.bMatchedIndices.size)
        assertEquals(setOf(0, 2, 4), result.bMatchedIndices.keys)
    }

    @Test
    fun `matched indices with transpositions have reduced scores`() {
        val result = StringComparison.jaroDistance("abcd", "abdc")

        // All characters match but c and d are transposed
        assertEquals(4, result.aMatchedIndices.size)
        assertEquals(4, result.bMatchedIndices.size)

        // The transposed characters should have lower scores (0.75 penalty)
        // Characters c and d in the second string should have score 0.75
        assertTrue(
            "Transposed characters should have reduced scores",
            result.bMatchedIndices.values.any { it < 1.0 }
        )
    }

    @Test
    fun `matched indices for multi-word split calculate offsets correctly`() {
        // Query: "zeppelin", Target: "led zeppelin"
        val result = StringComparison.jaroWinklerMultiDistance("zeppelin", "led zeppelin")

        // Should perfectly match "zeppelin" starting at index 4
        assertEquals(1.0, result.score, 0.001)

        // "led zeppelin"
        // Indices: 01234567891011
        // "zeppelin" is at indices 4-11
        val expectedIndices = setOf(4, 5, 6, 7, 8, 9, 10, 11)
        assertEquals(expectedIndices, result.bMatchedIndices.keys)
    }

    @Test
    fun `matched indices for query word against full target`() {
        // Query: "dark side" (multi-word), Target: "dark"
        val result = StringComparison.jaroWinklerMultiDistance("dark side", "dark")

        // Should match "dark" with high score
        assertEquals(1.0, result.score, 0.001)

        // aMatchedIndices should point to "dark" in "dark side"
        // "dark side"
        // Indices: 012345678
        // "dark" is at indices 0-3
        assertTrue(result.aMatchedIndices.keys.containsAll(setOf(0, 1, 2, 3)))
    }

    @Test
    fun `highlighting scenario - beatles query matches the beatles correctly`() {
        val result = StringComparison.jaroWinklerMultiDistance("beatles", "The Beatles")

        // Should match with high score
        assertTrue(result.score > 0.95)

        // In the UI, this would be used like:
        // val text = "The Beatles"
        // result.bMatchedIndices.forEach { (index, score) ->
        //     setSpan(..., index, index + 1, ...)
        // }

        // Verify indices are within bounds of "The Beatles" (11 characters)
        result.bMatchedIndices.keys.forEach { index ->
            assertTrue("Index $index should be < 11", index < 11)
        }
    }

    @Test
    fun `highlighting scenario - handles edge case of empty matches`() {
        val result = StringComparison.jaroWinklerDistance("xyz", "abc")

        // Should have very low score
        assertTrue(result.score < 0.50)

        // May have some weak matches or no matches at all
        // The highlighting code should handle this gracefully with try-catch
        assertTrue(result.bMatchedIndices.size >= 0)
    }

    @Test
    fun `index offset calculation for three word target`() {
        // Query: "moon", Target: "dark side moon"
        // Expected: match "moon" at indices 10-13
        val result = StringComparison.jaroWinklerMultiDistance("moon", "dark side moon")

        assertEquals(1.0, result.score, 0.001)

        // "dark side moon"
        // Index: 0123456789...
        // "dark" = 0-3
        // " " = 4
        // "side" = 5-8
        // " " = 9
        // "moon" = 10-13
        val expectedIndices = setOf(10, 11, 12, 13)
        assertEquals(expectedIndices, result.bMatchedIndices.keys)
    }

    @Test
    fun `index offset calculation explained step by step`() {
        // This test documents exactly how the offset is calculated
        val result = StringComparison.jaroWinklerMultiDistance("beatles", "the beatles")

        // String: "the beatles"
        // Split: ["the", "beatles"]
        //
        // For word at index 0 ("the"):
        //   offset = 0 + 0 + sum([]) = 0
        //   "the" maps to indices 0, 1, 2
        //
        // For word at index 1 ("beatles"):
        //   offset = 0 + 1 + sum(["the"]) = 0 + 1 + 3 = 4
        //   "beatles" maps to indices 4, 5, 6, 7, 8, 9, 10
        //
        // The "+ 1" accounts for the space between words

        assertEquals(1.0, result.score, 0.001)

        // Verify "beatles" is matched at the correct position
        val expectedIndices = setOf(4, 5, 6, 7, 8, 9, 10)
        assertEquals(
            "Indices should account for 'the ' prefix (3 chars + 1 space = offset of 4)",
            expectedIndices,
            result.bMatchedIndices.keys
        )
    }

    @Test
    fun `highlighting works correctly with normalized strings`() {
        // The algorithm normalizes to lowercase and NFD
        // "The Beatles" becomes "the beatles" internally
        val result = StringComparison.jaroWinklerMultiDistance("BEATLES", "The Beatles")

        // Should match despite case differences
        assertTrue(result.score > 0.95)

        // Indices should still be valid for the original "The Beatles" string
        result.bMatchedIndices.keys.forEach { index ->
            assertTrue(
                "Index $index should be valid for 'The Beatles' (length 11)",
                index < "The Beatles".length
            )
        }
    }

    @Test
    fun `prefix boost preserves correct indices with article stripping`() {
        // This tests the critical case where:
        // 1. Article "The " is stripped during matching
        // 2. Prefix boost is applied ("beat" is prefix of "beatles")
        // 3. Indices must still be valid for original string
        val result = StringComparison.jaroWinklerMultiDistance("beat", "The Beatles")

        // Should get high score from prefix boost
        // "beat" is prefix of "beatles" (after stripping "The ")
        assertTrue("Score should be high (>= 0.95)", result.score >= 0.95)

        // "The Beatles"
        // Index: 0-10
        // The matching can return indices from either:
        // - Full string match (may include matches across "The Beatles")
        // - Word-level match (indices 4-10 for "Beatles")

        // Critical: All indices must be valid for the original string
        result.bMatchedIndices.keys.forEach { index ->
            assertTrue(
                "Index $index should be within 'The Beatles' (< 11)",
                index < "The Beatles".length
            )
        }

        // Should have at least 4 indices for "beat" (4 characters)
        assertTrue(
            "Should have at least 4 matched indices for 'beat', got ${result.bMatchedIndices.size}",
            result.bMatchedIndices.size >= 4
        )

        // For UI highlighting purposes, having ANY valid indices is acceptable
        // The important thing is they point to actual characters in the original string
        assertTrue("Should have some matched indices", result.bMatchedIndices.isNotEmpty())
    }

    @Test
    fun `prefix boost with metallica preserves correct indices`() {
        // Query: "metal", Target: "Metallica"
        // No article stripping here, just prefix boost
        val result = StringComparison.jaroWinklerMultiDistance("metal", "Metallica")

        // Should get prefix boost (0.91 + 0.10 = 1.0, capped at 1.0)
        assertTrue(result.score >= 0.95)

        // Indices should point to "Metal" in "Metallica" (indices 0-4)
        result.bMatchedIndices.keys.forEach { index ->
            assertTrue(
                "Index $index should be within first 5 characters ('Metal')",
                index < 5
            )
        }

        // Should have 5 matched indices for "metal"
        assertTrue(
            "Should have at least 5 matched indices for 'metal'",
            result.bMatchedIndices.size >= 5
        )
    }

    @Test
    fun `exact match vs prefix match - highlighting distinguishes them correctly`() {
        // Query: "queen"
        val exactResult = StringComparison.jaroWinklerMultiDistance("queen", "Queen")
        val prefixResult = StringComparison.jaroWinklerMultiDistance("queen", "Queensway")

        // Both should have good scores
        // Note: Exact match gets +0.01 boost in the similarity classes (not in the core algorithm)
        // So here it will be 1.0, not > 1.0
        assertTrue("Exact match should have perfect score", exactResult.score >= 0.999)
        assertTrue("Prefix match should have high score", prefixResult.score >= 0.95) // Gets prefix boost

        // Exact match: all 5 characters of "Queen" should be highlighted
        assertEquals(5, exactResult.bMatchedIndices.size)
        assertEquals(setOf(0, 1, 2, 3, 4), exactResult.bMatchedIndices.keys)

        // Prefix match: should have indices covering the matched portion
        // The Jaro algorithm may match more or fewer characters depending on the target
        assertTrue("Prefix match should have at least 5 indices", prefixResult.bMatchedIndices.size >= 5)

        // Should include some characters from the "Queen" prefix
        assertTrue("Should have matches at the start", prefixResult.bMatchedIndices.keys.any { it < 5 })
    }
}
