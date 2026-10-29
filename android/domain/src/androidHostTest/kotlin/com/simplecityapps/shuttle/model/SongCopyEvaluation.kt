package com.simplecityapps.shuttle.model

import java.io.File
import kotlin.math.log2

/**
 * Scores [SongCopyMatcher] variants on the full song-match corpus (support/song-match/README.md), outside the unit-test
 * suite since the corpus lives outside the repo: `./gradlew :android:domain:evaluateSongCopyMatcher [-Pcorpus=<tsv>]`.
 */
fun main(args: Array<String>) {
    val path = args.firstOrNull() ?: "${System.getProperty("user.home")}/data/song-match/corpus/pairs.tsv"
    val pairs = parseSongCopyPairs(File(path).readText())
    val draft = SongCopyMatchParams()
    println("Corpus $path: ${pairs.size} pairs, ${pairs.count { it.same }} same, ${pairs.count { it.ambiguous }} ambiguous")
    println()

    val weights = estimateWeights(pairs, draft)
    val allFields = SongCopyField.entries.associateWith { SongCopyFieldUse.Score }
    val gates = draft.uses
    val variants = listOf(
        "a draft: block + title + duration 2 s + markers" to draft,
        "b no marker check" to draft.copy(markerCheck = false),
        "c duration 1 s" to draft.copy(durationToleranceMs = 1_000),
        "c duration 3 s" to draft.copy(durationToleranceMs = 3_000),
        "c duration 5 s" to draft.copy(durationToleranceMs = 5_000),
        "c duration ignored" to draft.copy(uses = gates - SongCopyField.Duration),
        "d album artist gate" to draft.copy(uses = gates + (SongCopyField.AlbumArtist to SongCopyFieldUse.Gate)),
        "d year gate" to draft.copy(uses = gates + (SongCopyField.Year to SongCopyFieldUse.Gate)),
        "d album artist + year gates" to draft.copy(uses = gates + (SongCopyField.AlbumArtist to SongCopyFieldUse.Gate) + (SongCopyField.Year to SongCopyFieldUse.Gate)),
        "d artist gate" to draft.copy(uses = gates + (SongCopyField.Artist to SongCopyFieldUse.Gate)),
        "e title edit distance 2" to draft.copy(titleEditDistance = 2),
        "e title edit distance 1" to draft.copy(titleEditDistance = 1),
        "x missing disc is unknown" to draft.copy(missingDiscIsOne = false),
        "x edition qualifiers only" to draft.copy(normalisation = draft.normalisation - SongCopyNormalisation.DropAllTitleQualifiers),
        "x no diacritic folding" to draft.copy(normalisation = draft.normalisation - SongCopyNormalisation.FoldDiacritics)
    ) + listOf(0.0, 5.0, 10.0, 15.0).map { threshold ->
        "f Fellegi-Sunter score >= $threshold" to draft.copy(uses = allFields, weights = weights, minScore = threshold)
    }

    println("%-48s %12s %12s".format("variant", "wrong-merge", "missed"))
    val results = variants.map { (name, params) -> Triple(name, params, evaluate(pairs, params)) }
    for ((name, _, result) in results) {
        println("%-48s %11.4f%% %11.3f%%".format(name, result.wrongMergeRate * 100, result.missedMergeRate * 100))
    }

    for ((name, _, result) in results) {
        println()
        println("== $name")
        println("%-40s %7s %5s %8s  %s".format("category", "pairs", "label", "merged", "failed gates (title/duration/other/no block)"))
        for ((category, counts) in result.categories.toSortedMap()) {
            println(
                "%-40s %7d %5s %7.2f%%  %d/%d/%d/%d".format(
                    category,
                    counts.pairs,
                    if (counts.same) "same" else "diff",
                    counts.merged * 100.0 / counts.pairs,
                    counts.titleFails,
                    counts.durationFails,
                    counts.otherFails,
                    counts.unblocked
                )
            )
        }
    }

    println()
    println("== m/u per field over blocked, unambiguous pairs with both values (draft normalisation)")
    println("%-12s %8s %8s %10s %10s".format("field", "m", "u", "agree w", "disagree w"))
    for ((field, estimate) in estimateMu(pairs, draft)) {
        val weight = weights.getValue(field)
        println("%-12s %8.4f %8.4f %10.2f %10.2f".format(field, estimate.first, estimate.second, weight.agree, weight.disagree))
    }
}

private class CategoryCounts(val same: Boolean) {
    var pairs = 0
    var merged = 0
    var titleFails = 0
    var durationFails = 0
    var otherFails = 0
    var unblocked = 0
}

private class Evaluation(val categories: Map<String, CategoryCounts>) {
    private val counted = categories.filterKeys { !it.startsWith("ambiguous_") }.values
    val wrongMergeRate = counted.filter { !it.same }.let { c -> c.sumOf { it.merged }.toDouble() / c.sumOf { it.pairs } }
    val missedMergeRate = counted.filter { it.same }.let { c -> 1 - c.sumOf { it.merged }.toDouble() / c.sumOf { it.pairs } }
}

private fun evaluate(
    pairs: List<SongCopyPair>,
    params: SongCopyMatchParams
): Evaluation {
    val matcher = SongCopyMatcher(params)
    val categories = HashMap<String, CategoryCounts>()
    for (pair in pairs) {
        val counts = categories.getOrPut(pair.category) { CategoryCounts(pair.same) }
        val match = matcher.match(pair.a, pair.b)
        counts.pairs++
        when {
            match.isCopy -> counts.merged++
            !match.blocked -> counts.unblocked++
            match.failedGate == SongCopyField.Title -> counts.titleFails++
            match.failedGate == SongCopyField.Duration -> counts.durationFails++
            else -> counts.otherFails++
        }
    }
    return Evaluation(categories)
}

/** m = P(agree | same song), u = P(agree | different songs), among pairs that share a block. */
private fun estimateMu(
    pairs: List<SongCopyPair>,
    params: SongCopyMatchParams
): Map<SongCopyField, Pair<Double, Double>> {
    val matcher = SongCopyMatcher(params)
    val prepared = pairs.filter { !it.ambiguous }.map { Triple(it.same, matcher.prepare(it.a), matcher.prepare(it.b)) }
        .filter { (_, a, b) -> a.blockingKey != null && a.blockingKey == b.blockingKey }
    return SongCopyField.entries.associateWith { field ->
        fun rate(same: Boolean): Double {
            val known = prepared.filter { it.first == same }.mapNotNull { (_, a, b) -> matcher.agreement(field, a, b) }
            // Add-one smoothing keeps a field that always agrees from getting an infinite weight
            return (known.count { it } + 1.0) / (known.size + 2.0)
        }
        rate(true) to rate(false)
    }
}

private fun estimateWeights(
    pairs: List<SongCopyPair>,
    params: SongCopyMatchParams
): Map<SongCopyField, SongCopyFieldWeight> = estimateMu(pairs, params).mapValues { (_, mu) ->
    val (m, u) = mu
    SongCopyFieldWeight(agree = log2(m / u), disagree = log2((1 - m) / (1 - u)))
}
