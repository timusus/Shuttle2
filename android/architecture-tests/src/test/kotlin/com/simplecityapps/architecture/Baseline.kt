package com.simplecityapps.architecture

import java.io.File
import org.junit.Assert.fail

/**
 * One rule violation. [entry] is the baseline line: a fully-qualified name, optionally followed by
 * ` -> dependency` or `: reason`; the design-system rule instead writes `path|Symbol|count`, a per-file
 * usage count that may only fall. [module] (the Gradle path) and [path] (relative to the repo root) only
 * feed the per-module report.
 */
data class Violation(val module: String, val entry: String, val path: String)

/**
 * A ratchet over a rule's violations. Each rule has `src/test/baselines/<rule>.txt` listing today's
 * violations, one per line. The rule fails on a violation that isn't in the baseline, and on a baseline
 * entry that no longer violates, so the file can only shrink.
 *
 * Most rules' entries are fully-qualified names (with ` -> dependency` or `: reason`); a rule that passes
 * `counted = true` (the design-system rule) writes `path|Symbol|count` entries instead, and [splitCountChanges]
 * ratchets the counts: a changed count is reported with its direction rather than as a new violation. Counts
 * only fall — regenerating the baseline may never raise one, and review enforces that.
 *
 * `./gradlew :android:architecture-tests:test -PupdateArchitectureBaselines` rewrites every baseline from
 * the current code. Use it after fixing violations; a baseline that grows needs a reason in review.
 */
object Baseline {
    private val baselineDir = File(requireNotNull(System.getProperty("architecture.baselineDir")))
    private val reportDir = File(requireNotNull(System.getProperty("architecture.reportDir")))
    private val update = System.getProperty("architecture.updateBaselines") == "true"

    fun assertMatches(rule: String, description: String, violations: Collection<Violation>, counted: Boolean = false) {
        val found = violations.map { it.entry }.toSortedSet()
        writeReport(rule, violations)

        val file = File(baselineDir, "$rule.txt")
        if (update) {
            file.writeText(
                buildString {
                    appendLine("# $description")
                    appendLine(
                        if (counted) {
                            "# Baseline of existing violations (#443), one `path|Symbol|count` per line. Counts only fall; see Baseline.kt."
                        } else {
                            "# Baseline of existing violations (#443). Only remove lines; see Baseline.kt."
                        },
                    )
                    found.forEach { appendLine(it) }
                },
            )
            return
        }

        val baseline = if (file.exists()) {
            file.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toSet()
        } else {
            emptySet()
        }
        val (new, fixed) = diff(baseline, found)
        if (new.isEmpty() && fixed.isEmpty()) return
        val (changes, newOnly, fixedOnly) = splitCountChanges(new, fixed)

        fail(
            buildString {
                appendLine("Architecture rule '$rule': $description")
                if (changes.isNotEmpty()) {
                    appendLine()
                    appendLine("${changes.size} entries changed count:")
                    changes.forEach { appendLine("  ${it.message()}") }
                }
                if (newOnly.isNotEmpty()) {
                    appendLine()
                    appendLine("${newOnly.size} new violations. Fix them rather than adding them to ${file.name}:")
                    newOnly.forEach { appendLine("  $it") }
                }
                if (fixedOnly.isNotEmpty()) {
                    appendLine()
                    appendLine("${fixedOnly.size} baseline entries no longer violate. Delete them from ${file.name}:")
                    fixedOnly.forEach { appendLine("  $it") }
                }
            },
        )
    }

    /**
     * Entries only in [found] (new) and only in [baseline] (fixed). A rule whose entries carry a count
     * (`path|Symbol|3`) gets a ratchet for free: a changed count is a new entry plus a fixed one.
     */
    fun diff(baseline: Set<String>, found: Set<String>): Pair<Set<String>, Set<String>> = (found - baseline) to (baseline - found)

    /** An entry `path|Symbol|count` whose count differs between baseline and code. */
    data class CountChange(val key: String, val from: Int, val to: Int) {
        fun message(): String = "$key: count went from $from to $to; " +
            if (to < from) "regenerate the baseline" else "remove the new usage"
    }

    /** Pairs [new] and [fixed] entries that differ only in a trailing numeric count. */
    fun splitCountChanges(new: Set<String>, fixed: Set<String>): Triple<List<CountChange>, Set<String>, Set<String>> {
        fun key(e: String) = e.substringBeforeLast('|')
        fun count(e: String) = e.substringAfterLast('|').toIntOrNull()
        val fixedByKey = fixed.filter { count(it) != null }.associateBy { key(it) }
        val changes = new.mapNotNull { n ->
            val old = fixedByKey[key(n)] ?: return@mapNotNull null
            val to = count(n) ?: return@mapNotNull null
            CountChange(key(n), count(old)!!, to)
        }
        val changed = changes.map { it.key }.toSet()
        return Triple(changes, new.filterNot { count(it) != null && key(it) in changed }.toSet(), fixed.filterNot { count(it) != null && key(it) in changed }.toSet())
    }

    /** Writes `build/reports/architecture/<rule>.tsv` (module, entry, path) for the audit's per-module counts. */
    private fun writeReport(rule: String, violations: Collection<Violation>) {
        reportDir.mkdirs()
        File(reportDir, "$rule.tsv").writeText(
            violations.distinct().sortedWith(compareBy({ it.module }, { it.entry })).joinToString("") { "${it.module}\t${it.entry}\t${it.path}\n" },
        )
    }
}
