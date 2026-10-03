package com.simplecityapps.architecture

import org.junit.Test

/**
 * Enforces design-language.md §5, deny by default (#794): outside `:android:designsystem`, every
 * `androidx.compose.material3.*` import is a violation unless [DesignSystemMatching.isAllowed] says it is a
 * token or state holder (theme, colour scheme, `*Defaults`, `*State`, opt-in markers, adaptive layout and window helpers).
 * A new M3 component is therefore flagged without anyone listing it.
 *
 * Konsist sees imports and text, not resolved call-site types, so the baseline entry is
 * `path|Symbol|count`: the file, the symbol, and how many times its name appears in code (comments, KDoc,
 * string literals and import lines are stripped first; a fully qualified use with no import counts too).
 * Adding a raw usage to an already-baselined file raises the count and fails; lowering it fails until the
 * baseline is regenerated. Counts are expected to only fall, and review enforces that regenerating never
 * raises one. docs/design/component-migration.md tracks the baseline by screen.
 */
class DesignSystemRules {

    @Test
    fun `feature modules do not import raw material3 components`() {
        val violations = Production.scope.files
            .filter { it.module != DESIGN_SYSTEM_MODULE }
            .flatMap { file ->
                DesignSystemMatching.entries(file.relativePath, file.imports.map { it.name to it.alias?.name }, file.text)
                    .map { file.violation(it) }
            }
        Baseline.assertMatches(
            "raw-material3-components",
            "Outside :android:designsystem, use the S2 component instead of a raw androidx.compose.material3 " +
                "component (design-language.md §5, docs/design/component-migration.md); if none exists yet, add it to " +
                "the designsystem first. Entries are path|Symbol|usages; the count may only go down",
            violations,
            counted = true,
        )
    }

    @Test
    fun `app sources use spacing and size tokens rather than literal dp values`() {
        val violations = Production.scope.files
            .filter { it.module == ":android:app" && "/src/main/" in it.relativePath }
            .mapNotNull { file ->
                val count = DesignSystemMatching.LITERAL_DP.findAll(DesignSystemMatching.stripNonCode(file.text)).count()
                if (count > 0) file.violation("${file.relativePath}|dp|$count") else null
            }
        Baseline.assertMatches(
            "literal-dp-values",
            "Use S2Spacing, S2IconSize, S2TouchTarget or another designsystem token instead of a literal N.dp in " +
                ":android:app; add a token when none fits. Entries are path|dp|count; the count may only go down",
            violations,
            counted = true,
        )
    }

    private companion object {
        const val DESIGN_SYSTEM_MODULE = ":android:designsystem"
    }
}

/** The matching logic of [DesignSystemRules], free of Konsist so it can be unit-tested. */
object DesignSystemMatching {
    const val M3_PREFIX = "androidx.compose.material3."

    /**
     * A literal `N.dp`, however Kotlin spells the number: a float suffix (`16f.dp`), a decimal (`2.5.dp`) or a
     * parenthesised negative (`(-16).dp`) all count. Zero is exempt, spelled any way (`0.dp`, `0.0.dp`, `0f.dp`).
     */
    val LITERAL_DP = Regex("(?<![\\w.])(?!0+(?:\\.0+)?f?\\)?\\.dp\\b)\\d+(?:\\.\\d+)?f?\\)?\\.dp\\b")

    /**
     * Layout, window and navigation3 helpers from `material3.adaptive`, relative to [M3_PREFIX]. Adaptive
     * components (NavigationSuiteScaffold, ListDetailPaneScaffold, AnimatedPane, ...) are deliberately absent.
     */
    private val ALLOWED_ADAPTIVE = setOf(
        "adaptive.ExperimentalMaterial3AdaptiveApi", "adaptive.HingeInfo", "adaptive.Posture",
        "adaptive.WindowAdaptiveInfo", "adaptive.currentWindowAdaptiveInfoV2",
        "adaptive.separatingHorizontalHingeBounds", "adaptive.separatingVerticalHingeBounds",
        "adaptive.layout.PaneScaffoldDirective", "adaptive.layout.calculatePaneScaffoldDirective",
        "adaptive.navigation3.ListDetailSceneStrategy", "adaptive.navigation3.rememberListDetailSceneStrategy",
    )

    /** Symbols that are theme, tokens or modifiers, not components. */
    private val ALLOWED_NAMES = setOf(
        "MaterialTheme", "MaterialExpressiveTheme", "ColorScheme", "Typography", "Shapes", "MotionScheme",
        "lightColorScheme", "darkColorScheme", "dynamicLightColorScheme", "dynamicDarkColorScheme",
        "LocalContentColor", "LocalTextStyle", "ProvideTextStyle", "ripple", "minimumInteractiveComponentSize",
        // Value types that parameterise or report on an S2 component.
        "SnackbarResult", "SnackbarDuration", "TopAppBarScrollBehavior", "ModalBottomSheetProperties",
    )

    /**
     * Name patterns for token and state holders, which have no S2 counterpart to migrate to. `.+Value` and
     * `.+(Shapes|Colors)` are accepted risk: a future component named like that would slip through.
     */
    private val ALLOWED_PATTERNS = listOf(
        Regex(".+Defaults"), // ButtonDefaults, TopAppBarDefaults, ...
        Regex(".+State"), // SnackbarHostState, SliderState, rememberModalBottomSheetState, ...
        Regex("Experimental\\w+Api"), // opt-in markers
        Regex(".+Value"), // WideNavigationRailValue, SwipeToDismissBoxValue, ...
        Regex(".+(Shapes|Colors)"), // ListItemShapes, ListItemColors, ...
    )

    private val QUALIFIED = Regex("androidx\\.compose\\.material3\\.([A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)*)")

    /** [import] is the full import name, e.g. `androidx.compose.material3.Text`. */
    fun isAllowed(import: String): Boolean {
        val name = import.removePrefix(M3_PREFIX)
        if (name.startsWith("adaptive.")) return name in ALLOWED_ADAPTIVE
        val simple = name.substringAfterLast('.')
        return simple in ALLOWED_NAMES || ALLOWED_PATTERNS.any { it.matches(simple) }
    }

    /**
     * Baseline entries `path|Symbol|count` for one file. [imports] are (import name, alias) pairs; [text] is the
     * file source. Comments, string literals and import lines are stripped, then the symbol (or its alias) is
     * counted. Fully qualified `androidx.compose.material3.X` uses with no import are counted too.
     */
    fun entries(path: String, imports: List<Pair<String, String?>>, text: String): List<String> {
        val body = stripNonCode(text)
        val counts = linkedMapOf<String, Int>()
        imports
            .filter { (name, _) -> name.startsWith(M3_PREFIX) && !isAllowed(name) }
            .forEach { (name, alias) ->
                val symbol = name.removePrefix(M3_PREFIX)
                val used = alias ?: symbol.substringAfterLast('.')
                counts[symbol] = Regex("\\b${Regex.escape(used)}\\b").findAll(body).count().coerceAtLeast(1)
            }
        QUALIFIED.findAll(body).forEach { match ->
            val segments = match.groupValues[1].split('.')
            val end = segments.indexOfFirst { it.first().isUpperCase() }.let { if (it < 0) segments.size else it + 1 }
            val symbol = segments.take(end).joinToString(".")
            if (symbol !in counts && !isAllowed(M3_PREFIX + symbol)) {
                val qualified = Regex.escape(M3_PREFIX + symbol) + "\\b"
                counts[symbol] = Regex(qualified).findAll(body).count()
            }
        }
        return counts.map { (symbol, count) -> "$path|$symbol|$count" }
    }

    /** [text] without line and block comments (nested, KDoc included), string and char literals, and imports. */
    fun stripNonCode(text: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                text.startsWith("//", i) -> while (i < text.length && text[i] != '\n') i++

                text.startsWith("/*", i) -> {
                    var depth = 0
                    do {
                        when {
                            text.startsWith("/*", i) -> {
                                depth++
                                i += 2
                            }

                            text.startsWith("*/", i) -> {
                                depth--
                                i += 2
                            }

                            else -> i++
                        }
                    } while (i < text.length && depth > 0)
                    out.append(' ')
                }

                text.startsWith("\"\"\"", i) -> {
                    val close = text.indexOf("\"\"\"", i + 3)
                    i = if (close < 0) text.length else close + 3
                    out.append(' ')
                }

                c == '"' || c == '\'' -> {
                    i++
                    while (i < text.length && text[i] != c && text[i] != '\n') i += if (text[i] == '\\') 2 else 1
                    i++
                    out.append(' ')
                }

                else -> {
                    out.append(c)
                    i++
                }
            }
        }
        return out.lineSequence().filterNot { it.trimStart().startsWith("import ") }.joinToString("\n")
    }
}
