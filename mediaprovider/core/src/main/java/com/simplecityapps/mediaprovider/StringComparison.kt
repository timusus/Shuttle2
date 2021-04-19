package com.simplecityapps.mediaprovider

import java.text.Normalizer
import kotlin.math.max
import kotlin.math.min

object StringComparison {

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
    fun jaroDistance(a: String, b: String): JaroSimilarity {
        if (a == b) return JaroSimilarity(
            score = 1.0,
            aMatchedIndices = a.mapIndexed { index, _ -> index to 1.0 }.toMap(),
            bMatchedIndices = b.mapIndexed { index, _ -> index to 1.0 }.toMap()
        )

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
    fun jaroWinklerDistance(a: String, b: String): JaroSimilarity {
        val a = Normalizer.normalize(a.toLowerCase(), Normalizer.Form.NFD)
        val b = Normalizer.normalize(b.toLowerCase(), Normalizer.Form.NFD)

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

    fun jaroWinklerMultiDistance(a: String, b: String): JaroSimilarity {
        val aSplit = a.split(" ")
        val bSplit = b.split(" ")

        return aSplit.flatMapIndexed { aIndex, a ->
            bSplit.mapIndexed { bIndex, b ->
                val jaroSimilarity = jaroWinklerDistance(a, b)
                jaroSimilarity.copy(
                    aMatchedIndices = jaroSimilarity.aMatchedIndices.mapKeys { it.key + aIndex + aSplit.take(aIndex).sumBy { it.length } },
                    bMatchedIndices = jaroSimilarity.bMatchedIndices.mapKeys { it.key + bIndex + bSplit.take(bIndex).sumBy { it.length } })
            }
        }.reduce { acc, jaroSimilarity ->
            JaroSimilarity(
                score = maxOf(acc.score, jaroSimilarity.score),
                aMatchedIndices = (acc.aMatchedIndices.asSequence() + jaroSimilarity.aMatchedIndices.asSequence()).groupBy({ it.key }, { it.value }).mapValues { it.value.maxOf { it } },
                bMatchedIndices = (acc.bMatchedIndices.asSequence() + jaroSimilarity.bMatchedIndices.asSequence()).groupBy({ it.key }, { it.value }).mapValues { it.value.maxOf { it } }
            )
        }
    }
}