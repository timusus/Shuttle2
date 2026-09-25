package com.simplecityapps.shuttle.designsystem.catalog

/**
 * Builds the review pages under `docs/design/catalog/`: `index.md`, the approval record with one
 * task-list line per component, and `<id>.md` with that component's boards.
 */
object CatalogPages {
    private val approvalLine = Regex("""^- \[[ xX]] `([a-z0-9-]+)`:.*$""")

    /**
     * The index for [entries]. A component already listed in [existingIndex] keeps its line as it
     * is, so a tick, approval date and boards hash survive a re-record; new components get an
     * unticked line and removed ones drop out.
     */
    fun index(entries: List<CatalogEntry>, existingIndex: String?): String {
        val existing = existingIndex.orEmpty().lines()
            .mapNotNull { line -> approvalLine.matchEntire(line)?.let { it.groupValues[1] to line } }
            .toMap()
        val lines = entries.map { entry ->
            existing[entry.id] ?: "- [ ] `${entry.id}`: [boards](${entry.id}.md) · approved: — · boards hash: —"
        }
        return buildString {
            appendLine("# Component catalogue")
            appendLine()
            appendLine("The approval record for the design system (`docs/design/design-language.md` §5). Each line opens")
            appendLine("a component's boards. Approve one by ticking it; the commit records the date, the commit hash and")
            appendLine("a hash of the component's PNGs. Approval is asynchronous: screens may use a component before it is")
            appendLine("ticked, and a change to a component flows to every screen that uses it.")
            appendLine()
            appendLine("Boards are recorded by `support/scripts/catalog`, which also rewrites this file and keeps")
            appendLine("existing lines as they are.")
            appendLine()
            lines.forEach(::appendLine)
        }
    }

    /** The review page for [entry]: its states, then its boards grouped by width, theme and font scale. */
    fun componentPage(entry: CatalogEntry, shots: List<CatalogShot>): String = buildString {
        appendLine("# `${entry.id}`: ${entry.title}")
        appendLine()
        appendLine("[All components](index.md)")
        appendLine()
        appendLine("States: ${entry.states.joinToString("; ")}.")
        shots.filter { it.entry == entry }
            .sortedWith(compareBy({ it.width }, { it.darkTheme }, { it.fontScale }))
            .groupBy { Triple(it.width, it.darkTheme, it.fontScale) }
            .forEach { (key, group) ->
                val (width, dark, fontScale) = key
                appendLine()
                append("## ${width.name}, ${if (dark) "dark" else "light"}")
                if (fontScale != 1f) append(", font scale ${fontScale.toString().removeSuffix(".0")}")
                appendLine()
                appendLine()
                val imageWidth = if (width == BoardWidth.Compact) 200 else 480
                appendLine(group.joinToString(" | ", "| ", " |") { it.scheme.label })
                appendLine(group.joinToString(" | ", "| ", " |") { "---" })
                appendLine(group.joinToString(" | ", "| ", " |") { """<img src="${it.path}" width="$imageWidth" alt="${it.scheme.label}">""" })
            }
    }

    private val CatalogScheme.label: String
        get() = when (this) {
            CatalogScheme.LowChroma -> "Low-chroma seed"
            CatalogScheme.Brand -> "Brand"
            else -> "$name seed"
        }
}
