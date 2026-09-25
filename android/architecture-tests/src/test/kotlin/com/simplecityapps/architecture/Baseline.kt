package com.simplecityapps.architecture

import java.io.File
import org.junit.Assert.fail

/**
 * One rule violation. [entry] is the baseline line: a fully-qualified name, optionally followed by
 * ` -> dependency` or `: reason`. [module] (the Gradle path) and [path] (relative to the repo root) only
 * feed the per-module report.
 */
data class Violation(val module: String, val entry: String, val path: String)

/**
 * A ratchet over a rule's violations. Each rule has `src/test/baselines/<rule>.txt` listing today's
 * violations, one per line. The rule fails on a violation that isn't in the baseline, and on a baseline
 * entry that no longer violates, so the file can only shrink.
 *
 * `./gradlew :android:architecture-tests:test -PupdateArchitectureBaselines` rewrites every baseline from
 * the current code. Use it after fixing violations; a baseline that grows needs a reason in review.
 */
object Baseline {
    private val baselineDir = File(requireNotNull(System.getProperty("architecture.baselineDir")))
    private val reportDir = File(requireNotNull(System.getProperty("architecture.reportDir")))
    private val update = System.getProperty("architecture.updateBaselines") == "true"

    fun assertMatches(rule: String, description: String, violations: Collection<Violation>) {
        val found = violations.map { it.entry }.toSortedSet()
        writeReport(rule, violations)

        val file = File(baselineDir, "$rule.txt")
        if (update) {
            file.writeText(
                buildString {
                    appendLine("# $description")
                    appendLine("# Baseline of existing violations (#443). Only remove lines; see Baseline.kt.")
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
        val new = found - baseline
        val fixed = baseline - found
        if (new.isEmpty() && fixed.isEmpty()) return

        fail(
            buildString {
                appendLine("Architecture rule '$rule': $description")
                if (new.isNotEmpty()) {
                    appendLine()
                    appendLine("${new.size} new violations. Fix them rather than adding them to ${file.name}:")
                    new.forEach { appendLine("  $it") }
                }
                if (fixed.isNotEmpty()) {
                    appendLine()
                    appendLine("${fixed.size} baseline entries no longer violate. Delete them from ${file.name}:")
                    fixed.forEach { appendLine("  $it") }
                }
            },
        )
    }

    /** Writes `build/reports/architecture/<rule>.tsv` (module, entry, path) for the audit's per-module counts. */
    private fun writeReport(rule: String, violations: Collection<Violation>) {
        reportDir.mkdirs()
        File(reportDir, "$rule.tsv").writeText(
            violations.distinct().sortedWith(compareBy({ it.module }, { it.entry })).joinToString("") { "${it.module}\t${it.entry}\t${it.path}\n" },
        )
    }
}
