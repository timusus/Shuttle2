package com.simplecityapps.mediaprovider

import kotlin.system.measureNanoTime
import org.junit.Test

/**
 * Performance benchmarks for string comparison and search algorithms.
 *
 * These tests measure actual execution time to identify performance bottlenecks
 * and ensure the search remains responsive even with large libraries.
 *
 * Target performance goals:
 * - Single Jaro calculation: < 10μs (microseconds) for typical strings
 * - Multi-word calculation: < 50μs for typical queries
 * - Full library search (1000 items): < 100ms total
 * - Full library search (5000 items): < 500ms total
 */
class PerformanceBenchmarkTest {

    private data class BenchmarkResult(
        val operation: String,
        val iterations: Int,
        val totalTimeMs: Long,
        val avgTimeNs: Long,
        val avgTimeUs: Double = avgTimeNs / 1000.0,
        val avgTimeMs: Double = avgTimeNs / 1_000_000.0
    ) {
        override fun toString(): String = when {
            avgTimeMs >= 1.0 -> "$operation: avg ${String.format("%.2f", avgTimeMs)}ms ($iterations iterations, total ${totalTimeMs}ms)"
            avgTimeUs >= 1.0 -> "$operation: avg ${String.format("%.2f", avgTimeUs)}μs ($iterations iterations, total ${totalTimeMs}ms)"
            else -> "$operation: avg ${avgTimeNs}ns ($iterations iterations, total ${totalTimeMs}ms)"
        }
    }

    private fun benchmark(operation: String, iterations: Int = 1000, block: () -> Unit): BenchmarkResult {
        // Warm-up
        repeat(10) { block() }

        // Measure
        val totalTimeNs = measureNanoTime {
            repeat(iterations) {
                block()
            }
        }

        return BenchmarkResult(
            operation = operation,
            iterations = iterations,
            totalTimeMs = totalTimeNs / 1_000_000,
            avgTimeNs = totalTimeNs / iterations
        )
    }

    // ===================================================================================
    // CORE ALGORITHM BENCHMARKS
    // ===================================================================================

    @Test
    fun `benchmark - single jaroDistance calculation`() {
        val results = listOf(
            benchmark("jaroDistance short strings (5 chars)") {
                StringComparison.jaroDistance("hello", "hella")
            },
            benchmark("jaroDistance medium strings (15 chars)") {
                StringComparison.jaroDistance("the beatles", "the bee gees")
            },
            benchmark("jaroDistance long strings (40 chars)") {
                StringComparison.jaroDistance(
                    "the dark side of the moon pink floyd",
                    "dark side of the moon remastered 2011"
                )
            },
            benchmark("jaroDistance exact match") {
                StringComparison.jaroDistance("the beatles", "the beatles")
            },
            benchmark("jaroDistance no match") {
                StringComparison.jaroDistance("aaaaa", "bbbbb")
            }
        )

        println("\n=== Core Jaro Distance Performance ===")
        results.forEach { println(it) }
    }

    @Test
    fun `benchmark - jaroWinklerDistance calculation`() {
        val results = listOf(
            benchmark("jaroWinklerDistance short") {
                StringComparison.jaroWinklerDistance("beat", "beatles")
            },
            benchmark("jaroWinklerDistance medium") {
                StringComparison.jaroWinklerDistance("dark side", "the dark side of the moon")
            },
            benchmark("jaroWinklerDistance with normalization") {
                StringComparison.jaroWinklerDistance("café", "cafe")
            }
        )

        println("\n=== Jaro-Winkler Distance Performance ===")
        results.forEach { println(it) }
    }

    @Test
    fun `benchmark - jaroWinklerMultiDistance single word`() {
        val results = listOf(
            benchmark("multiDistance single word - simple") {
                StringComparison.jaroWinklerMultiDistance("beatles", "The Beatles")
            },
            benchmark("multiDistance single word - partial") {
                StringComparison.jaroWinklerMultiDistance("zeppelin", "Led Zeppelin")
            },
            benchmark("multiDistance single word - multi-word target") {
                StringComparison.jaroWinklerMultiDistance("queen", "Queens of the Stone Age")
            }
        )

        println("\n=== Multi-Distance Single Word Performance ===")
        results.forEach { println(it) }
    }

    @Test
    fun `benchmark - jaroWinklerMultiDistance multi word`() {
        val results = listOf(
            benchmark("multiDistance 2 words vs 2 words") {
                StringComparison.jaroWinklerMultiDistance("dark side", "The Dark Side")
            },
            benchmark("multiDistance 2 words vs 7 words") {
                StringComparison.jaroWinklerMultiDistance("dark side", "The Dark Side of the Moon")
            },
            benchmark("multiDistance 3 words vs 7 words") {
                StringComparison.jaroWinklerMultiDistance("queens stone age", "Queens of the Stone Age")
            }
        )

        println("\n=== Multi-Distance Multi-Word Performance ===")
        results.forEach { println(it) }
    }

    // ===================================================================================
    // REALISTIC SEARCH BENCHMARKS
    // ===================================================================================

    @Test
    fun `benchmark - search through 100 items`() {
        val library = generateRealisticLibrary(100)
        val queries = listOf("beatles", "dark side", "queen", "led zeppelin")

        queries.forEach { query ->
            val result = benchmark("Search 100 items for '$query'", iterations = 100) {
                library.filter { target ->
                    StringComparison.jaroWinklerMultiDistance(query, target).score > StringComparison.threshold
                }
            }
            println(result)
        }
    }

    @Test
    fun `benchmark - search through 500 items`() {
        val library = generateRealisticLibrary(500)
        val queries = listOf("beatles", "dark side", "queen")

        queries.forEach { query ->
            val result = benchmark("Search 500 items for '$query'", iterations = 20) {
                library.filter { target ->
                    StringComparison.jaroWinklerMultiDistance(query, target).score > StringComparison.threshold
                }
            }
            println(result)
        }
    }

    @Test
    fun `benchmark - search through 1000 items`() {
        val library = generateRealisticLibrary(1000)
        val queries = listOf("beatles", "dark side", "queen")

        queries.forEach { query ->
            val result = benchmark("Search 1000 items for '$query'", iterations = 10) {
                library.filter { target ->
                    StringComparison.jaroWinklerMultiDistance(query, target).score > StringComparison.threshold
                }
            }
            println(result)
        }
    }

    @Test
    fun `benchmark - search through 5000 items (large library)`() {
        val library = generateRealisticLibrary(5000)
        val query = "beatles"

        val result = benchmark("Search 5000 items for '$query'", iterations = 5) {
            library.filter { target ->
                StringComparison.jaroWinklerMultiDistance(query, target).score > StringComparison.threshold
            }
        }
        println(result)
    }

    @Test
    fun `benchmark - full search with sorting (realistic usage)`() {
        val library = generateRealisticLibrary(1000)

        val result = benchmark("Full search + sort 1000 items", iterations = 10) {
            library
                .map { target ->
                    target to StringComparison.jaroWinklerMultiDistance("dark side", target)
                }
                .filter { it.second.score > StringComparison.threshold }
                .sortedWith(
                    compareByDescending<Pair<String, StringComparison.JaroSimilarity>> { it.second.score }
                        .thenBy { it.first.length }
                )
                .take(50) // Top 50 results
        }
        println(result)
    }

    // ===================================================================================
    // WORST CASE SCENARIOS
    // ===================================================================================

    @Test
    fun `benchmark - worst case - many similar prefixes`() {
        // Worst case: many items with same prefix (e.g., "The")
        val library = List(1000) { i -> "The Band $i" } + listOf("The Beatles")

        val result = benchmark("Search 1000 'The' bands for 'beatles'", iterations = 10) {
            library.filter { target ->
                StringComparison.jaroWinklerMultiDistance("beatles", target).score > StringComparison.threshold
            }
        }
        println(result)
    }

    @Test
    fun `benchmark - worst case - long multi-word query vs long targets`() {
        val longTarget = "The World Is a Beautiful Place & I Am No Longer Afraid to Die"
        val longQuery = "beautiful place afraid die"

        val result = benchmark("Long multi-word query (4 words) vs long target (14 words)", iterations = 1000) {
            StringComparison.jaroWinklerMultiDistance(longQuery, longTarget)
        }
        println(result)
    }

    // ===================================================================================
    // DETAILED PROFILING BREAKDOWN
    // ===================================================================================

    @Test
    fun `profile - breakdown of multi-word distance components`() {
        val query = "dark side"
        val target = "The Dark Side of the Moon"

        var fullStringTime = 0L
        var prefixCheckTime = 0L
        var singleWordMatchTime = 0L
        var multiWordMatchTime = 0L
        var coverageBonusTime = 0L

        val iterations = 1000

        // Measure full operation
        val totalTime = measureNanoTime {
            repeat(iterations) {
                StringComparison.jaroWinklerMultiDistance(query, target)
            }
        }

        // Measure individual components
        fullStringTime = measureNanoTime {
            repeat(iterations) {
                StringComparison.jaroWinklerDistance(query, target)
            }
        }

        val querySplit = query.split(" ")
        val targetSplit = target.split(" ")

        singleWordMatchTime = measureNanoTime {
            repeat(iterations) {
                targetSplit.forEach { targetWord ->
                    StringComparison.jaroWinklerDistance(query, targetWord)
                }
            }
        }

        multiWordMatchTime = measureNanoTime {
            repeat(iterations) {
                querySplit.forEach { queryWord ->
                    targetSplit.forEach { targetWord ->
                        StringComparison.jaroWinklerDistance(queryWord, targetWord)
                    }
                }
            }
        }

        println("\n=== Multi-Word Distance Breakdown ===")
        println("Total time: ${totalTime / 1_000_000.0}ms (avg ${(totalTime / iterations) / 1000.0}μs per call)")
        println("  Full string match: ${fullStringTime / 1_000_000.0}ms (${fullStringTime * 100 / totalTime}% of total)")
        println("  Single word matches (${targetSplit.size} calls): ${singleWordMatchTime / 1_000_000.0}ms")
        println("  Multi-word matches (${querySplit.size * targetSplit.size} calls): ${multiWordMatchTime / 1_000_000.0}ms")
    }

    @Test
    fun `profile - article stripping overhead`() {
        val withArticle = "The Beatles"
        val withoutArticle = "Beatles"

        val withArticleResult = benchmark("jaroWinklerMultiDistance with article") {
            StringComparison.jaroWinklerMultiDistance("beatles", withArticle)
        }

        val withoutArticleResult = benchmark("jaroWinklerMultiDistance without article") {
            StringComparison.jaroWinklerMultiDistance("beatles", withoutArticle)
        }

        println("\n=== Article Stripping Overhead ===")
        println(withArticleResult)
        println(withoutArticleResult)
        val overhead = withArticleResult.avgTimeNs - withoutArticleResult.avgTimeNs
        println("Overhead: ${overhead / 1000.0}μs (${(overhead * 100.0 / withArticleResult.avgTimeNs).toInt()}%)")
    }

    @Test
    fun `profile - normalization overhead`() {
        val normalized = "beatles"
        val withAccents = "bëátlés"

        val normalizedResult = benchmark("jaroWinklerDistance normalized") {
            StringComparison.jaroWinklerDistance(normalized, "the beatles")
        }

        val withAccentsResult = benchmark("jaroWinklerDistance with accents") {
            StringComparison.jaroWinklerDistance(withAccents, "the beatles")
        }

        println("\n=== Unicode Normalization Overhead ===")
        println(normalizedResult)
        println(withAccentsResult)
        val overhead = withAccentsResult.avgTimeNs - normalizedResult.avgTimeNs
        println("Overhead: ${overhead / 1000.0}μs (${(overhead * 100.0 / normalizedResult.avgTimeNs).toInt()}%)")
    }

    // ===================================================================================
    // FTS PERFORMANCE COMPARISON
    // ===================================================================================

    @Test
    fun `benchmark - FTS search strategy comparison`() {
        val library = generateRealisticLibrary(5000)
        val query = "beatles"

        println("\n=== FTS vs Full Scan Performance Comparison ===")
        println("Library size: ${library.size} items")
        println("Query: '$query'")
        println()

        // Simulate OLD approach: Full scan with Jaro-Winkler on every item
        val oldApproachResult = benchmark("OLD: Full scan + Jaro-Winkler on 5000 items", iterations = 5) {
            library
                .map { target -> target to StringComparison.jaroWinklerMultiDistance(query, target) }
                .filter { it.second.score > StringComparison.threshold }
                .sortedWith(
                    compareByDescending<Pair<String, StringComparison.JaroSimilarity>> { it.second.score }
                        .thenBy { it.first.length }
                )
                .take(50)
        }

        println("\n--- OLD APPROACH (Full Scan) ---")
        println(oldApproachResult)

        // Simulate NEW approach with FTS:
        // In practice, FTS would filter down to ~100 candidates in <10ms
        // Here we simulate by taking a random subset (in reality FTS uses indexed search)
        // Then apply Jaro-Winkler only on those candidates
        val newApproachResult = benchmark("NEW: FTS pre-filter + Jaro-Winkler on ~100 candidates", iterations = 5) {
            // Simulate FTS returning ~100 candidates (this would be <10ms with real FTS)
            val ftsCandidates = library
                .filter {
                    it.contains("beatles", ignoreCase = true) ||
                        it.contains("beat", ignoreCase = true)
                }
                .take(100)

            // Apply Jaro-Winkler only on FTS candidates
            ftsCandidates
                .map { target -> target to StringComparison.jaroWinklerMultiDistance(query, target) }
                .filter { it.second.score > StringComparison.threshold }
                .sortedWith(
                    compareByDescending<Pair<String, StringComparison.JaroSimilarity>> { it.second.score }
                        .thenBy { it.first.length }
                )
                .take(50)
        }

        println("\n--- NEW APPROACH (FTS Pre-filtering) ---")
        println(newApproachResult)

        val improvement = ((oldApproachResult.avgTimeMs - newApproachResult.avgTimeMs) / oldApproachResult.avgTimeMs * 100)
        println("\n--- PERFORMANCE IMPROVEMENT ---")
        println("Speedup: ${String.format("%.1f", oldApproachResult.avgTimeMs / newApproachResult.avgTimeMs)}x faster")
        println("Improvement: ${String.format("%.1f", improvement)}%")
        println("Time saved: ${String.format("%.2f", oldApproachResult.avgTimeMs - newApproachResult.avgTimeMs)}ms per search")
    }

    @Test
    fun `benchmark - FTS candidate set sizes`() {
        val library = generateRealisticLibrary(5000)

        println("\n=== FTS Candidate Set Size Analysis ===")
        println("Library size: ${library.size} items")
        println()

        val queries = listOf("beatles", "dark side", "queen", "led zeppelin", "xyz")

        queries.forEach { query ->
            // Simulate FTS candidate filtering
            val candidates = library.filter { target ->
                val words = query.split(" ")
                words.any { word -> target.contains(word, ignoreCase = true) }
            }

            val jaroMatches = candidates
                .map { target -> StringComparison.jaroWinklerMultiDistance(query, target) }
                .count { it.score > StringComparison.threshold }

            println("Query: '$query'")
            println("  FTS candidates: ${candidates.size} (${(candidates.size * 100.0 / library.size).toInt()}% of library)")
            println("  Jaro matches: $jaroMatches")
            println("  Reduction: ${String.format("%.1f", 100.0 - (candidates.size * 100.0 / library.size))}% fewer comparisons")
            println()
        }
    }

    // ===================================================================================
    // MEMORY ALLOCATION TESTS
    // ===================================================================================

    @Test
    fun `benchmark - object allocation overhead`() {
        val query = "beatles"
        val target = "The Beatles"

        // Measure with full JaroSimilarity object creation
        val withObjectsResult = benchmark("With JaroSimilarity objects") {
            StringComparison.jaroWinklerMultiDistance(query, target)
        }

        // Measure just the core algorithm (if we only needed the score)
        val justScoreResult = benchmark("Just score calculation") {
            StringComparison.jaroWinklerDistance(query, target).score
        }

        println("\n=== Object Allocation Overhead ===")
        println(withObjectsResult)
        println(justScoreResult)
    }

    // ===================================================================================
    // HELPER FUNCTIONS
    // ===================================================================================

    private fun generateRealisticLibrary(size: Int): List<String> {
        val realArtists = listOf(
            "The Beatles", "Led Zeppelin", "Pink Floyd", "Queen", "The Rolling Stones",
            "David Bowie", "Radiohead", "Nirvana", "The Who", "The Doors",
            "Metallica", "AC/DC", "Black Sabbath", "Deep Purple", "Jimi Hendrix",
            "Bob Dylan", "The Clash", "Sex Pistols", "The Smiths", "Joy Division",
            "U2", "R.E.M.", "Pearl Jam", "Soundgarden", "Alice in Chains",
            "Red Hot Chili Peppers", "Foo Fighters", "Green Day", "The Strokes", "Arctic Monkeys",
            "Arcade Fire", "Vampire Weekend", "Tame Impala", "MGMT", "The National",
            "LCD Soundsystem", "Yeah Yeah Yeahs", "Interpol", "Bloc Party", "Franz Ferdinand",
            "Kings of Leon", "The Killers", "Muse", "Coldplay", "Oasis"
        )

        val realAlbums = listOf(
            "Abbey Road", "Dark Side of the Moon", "Led Zeppelin IV", "Nevermind",
            "OK Computer", "The Wall", "Sgt. Pepper's Lonely Hearts Club Band",
            "London Calling", "Rumours", "Hotel California", "Born to Run",
            "Blood Sugar Sex Magik", "Ten", "The Joshua Tree", "Achtung Baby",
            "Blue", "Pet Sounds", "What's Going On", "Kind of Blue", "Thriller"
        )

        val realSongs = listOf(
            "Stairway to Heaven", "Bohemian Rhapsody", "Imagine", "Hey Jude",
            "Smells Like Teen Spirit", "Billie Jean", "Like a Rolling Stone",
            "Purple Haze", "What's Going On", "Good Vibrations"
        )

        val prefixes = listOf("The", "A", "Los", "La", "", "")
        val suffixes = listOf("", " Band", " Project", " & Friends", " Experience")

        val library = mutableListOf<String>()
        library.addAll(realArtists)
        library.addAll(realAlbums)
        library.addAll(realSongs)

        // Generate synthetic entries to reach target size
        var counter = 0
        while (library.size < size) {
            when (counter % 3) {
                0 -> library.add("${prefixes.random()} ${realArtists.random().split(" ").last()} ${suffixes.random()}")
                1 -> library.add("${realSongs.random().split(" ").first()} ${realAlbums.random().split(" ").last()}")
                else -> library.add("Artist $counter")
            }
            counter++
        }

        return library.take(size)
    }
}
