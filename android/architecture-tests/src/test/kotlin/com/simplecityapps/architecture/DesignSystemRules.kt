package com.simplecityapps.architecture

import org.junit.Test

/**
 * Enforces design-language.md §5, deny by default (#794): outside `:android:designsystem`, every
 * `androidx.compose.material3.*` import is a violation unless [DesignSystemMatching.isAllowed] says it is a
 * token or state holder (theme, colour scheme, `*Defaults`, `*State`, opt-in markers, adaptive layout APIs).
 * A new M3 component is therefore flagged without anyone listing it.
 *
 * Konsist sees imports and text, not resolved call-site types, so the baseline entry is
 * `path|Symbol|count`: the file, the imported symbol, and how many times its name appears outside import
 * lines (word match, so comments count). Adding a raw usage to an already-baselined file raises the count,
 * which fails as a new entry; lowering it fails as a stale one until the baseline is regenerated, so the
 * count can only go down. docs/design/component-migration.md tracks the baseline by screen.
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
        )
    }

    private companion object {
        const val DESIGN_SYSTEM_MODULE = ":android:designsystem"
    }
}

/** The matching logic of [DesignSystemRules], free of Konsist so it can be unit-tested. */
object DesignSystemMatching {
    const val M3_PREFIX = "androidx.compose.material3."

    /** Subpackages of material3 that are layout/window APIs rather than components. */
    private const val ADAPTIVE_PREFIX = "adaptive."

    /** Symbols that are theme, tokens or modifiers, not components. */
    private val ALLOWED_NAMES = setOf(
        "MaterialTheme", "MaterialExpressiveTheme", "ColorScheme", "Typography", "Shapes", "MotionScheme",
        "lightColorScheme", "darkColorScheme", "dynamicLightColorScheme", "dynamicDarkColorScheme",
        "LocalContentColor", "LocalTextStyle", "ProvideTextStyle", "ripple", "minimumInteractiveComponentSize",
        // Value types that parameterise or report on an S2 component.
        "SnackbarResult", "SnackbarDuration", "TopAppBarScrollBehavior", "ModalBottomSheetProperties",
    )

    /** Name patterns for token and state holders, which have no S2 counterpart to migrate to. */
    private val ALLOWED_PATTERNS = listOf(
        Regex(".+Defaults"), // ButtonDefaults, TopAppBarDefaults, ...
        Regex(".+State"), // SnackbarHostState, SliderState, SheetState, ...
        Regex("remember.+State"),
        Regex("Experimental\\w+Api"), // opt-in markers
        Regex(".+Value"), // WideNavigationRailValue, SwipeToDismissBoxValue, ...
        Regex(".+(Shapes|Colors)"), // ListItemShapes, ListItemColors, ...
    )

    /** [import] is the full import name, e.g. `androidx.compose.material3.Text`. */
    fun isAllowed(import: String): Boolean {
        val name = import.removePrefix(M3_PREFIX)
        if (name.startsWith(ADAPTIVE_PREFIX)) return true
        val simple = name.substringAfterLast('.')
        return simple in ALLOWED_NAMES || ALLOWED_PATTERNS.any { it.matches(simple) }
    }

    /**
     * Baseline entries `path|Symbol|count` for one file. [imports] are (import name, alias) pairs; [text] is the
     * file source, searched for the symbol (or its alias) outside import lines.
     */
    fun entries(path: String, imports: List<Pair<String, String?>>, text: String): List<String> {
        val body = text.lineSequence().filterNot { it.trimStart().startsWith("import ") }.joinToString("\n")
        return imports
            .filter { (name, _) -> name.startsWith(M3_PREFIX) && !isAllowed(name) }
            .map { (name, alias) ->
                val symbol = name.removePrefix(M3_PREFIX)
                val used = alias ?: symbol.substringAfterLast('.')
                val count = Regex("\\b${Regex.escape(used)}\\b").findAll(body).count().coerceAtLeast(1)
                "$path|$symbol|$count"
            }
            .distinct()
    }
}
