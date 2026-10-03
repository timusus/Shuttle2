package com.simplecityapps.architecture

import org.junit.Test

/**
 * Enforces design-language.md §5: screens build their UI from `:android:designsystem`, not from raw
 * `androidx.compose.material3` components. Theme and token access (`MaterialTheme`, `*Defaults`,
 * state holders, opt-in markers, adaptive APIs) stays allowed; only the composables listed in
 * [RAW_COMPONENTS] are flagged. Konsist can see imports but not call-site types, so this is an import
 * rule (the only way to reach a raw component without a wildcard, which ktlint already forbids).
 * The baseline lists today's imports per file; docs/design/component-migration.md tracks them by screen.
 */
class DesignSystemRules {

    @Test
    fun `feature modules do not import raw material3 components`() {
        val violations = Production.scope.files
            .filter { it.module != DESIGN_SYSTEM_MODULE }
            .flatMap { file ->
                file.importNames
                    .filter { it.startsWith(M3_PREFIX) && it.removePrefix(M3_PREFIX) in RAW_COMPONENTS }
                    .map { file.violation("${file.fqn} -> $it") }
            }
        Baseline.assertMatches(
            "raw-material3-components",
            "Outside :android:designsystem, import the S2 component instead of a raw androidx.compose.material3 " +
                "component (design-language.md §5, docs/design/component-migration.md); if none exists yet, add it to " +
                "the designsystem first",
            violations,
        )
    }

    private companion object {
        const val DESIGN_SYSTEM_MODULE = ":android:designsystem"
        const val M3_PREFIX = "androidx.compose.material3."

        val RAW_COMPONENTS = setOf(
            "AlertDialog", "BasicAlertDialog", "Badge", "BadgedBox", "BottomAppBar", "Button", "Card", "Checkbox",
            "CircularProgressIndicator", "ContainedLoadingIndicator", "DropdownMenu", "DropdownMenuItem",
            "ElevatedButton", "ElevatedCard", "ExtendedFloatingActionButton", "FilledTonalButton",
            "FilledTonalIconButton", "FilledIconButton", "FilterChip", "FloatingActionButton", "HorizontalDivider",
            "Icon", "IconButton", "IconToggleButton", "InputChip", "LargeTopAppBar", "LinearProgressIndicator",
            "LinearWavyProgressIndicator", "ListItem", "LoadingIndicator", "MediumTopAppBar", "ModalBottomSheet",
            "NavigationBar", "NavigationBarItem", "NavigationRail", "NavigationRailItem", "OutlinedButton",
            "OutlinedCard", "OutlinedIconButton", "OutlinedTextField", "RadioButton", "Scaffold", "SearchBar",
            "ShortNavigationBar", "ShortNavigationBarItem", "Slider", "Snackbar", "SnackbarHost", "Surface", "Switch",
            "SwipeToDismissBox", "Tab", "TabRow", "Text", "TextButton", "TextField", "ToggleButton", "TopAppBar",
            "VerticalDivider", "WideNavigationRail", "WideNavigationRailItem",
            "pulltorefresh.PullToRefreshBox",
        )
    }
}
