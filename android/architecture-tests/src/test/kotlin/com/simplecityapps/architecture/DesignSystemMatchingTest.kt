package com.simplecityapps.architecture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignSystemMatchingTest {

    private val m3 = DesignSystemMatching.M3_PREFIX

    @Test
    fun `token and state imports are allowed`() {
        listOf(
            "MaterialTheme",
            "ColorScheme",
            "LocalContentColor",
            "ButtonDefaults",
            "SnackbarHostState",
            "rememberModalBottomSheetState",
            "ExperimentalMaterial3Api",
            "adaptive.WindowAdaptiveInfo",
        ).forEach { assertTrue(it, DesignSystemMatching.isAllowed(m3 + it)) }
        assertTrue(DesignSystemMatching.entries("a/B.kt", listOf(m3 + "MaterialTheme" to null), "MaterialTheme").isEmpty())
    }

    @Test
    fun `component imports are flagged, including ones nobody listed`() {
        listOf("Text", "Card", "pulltorefresh.PullToRefreshBox", "SomeBrandNewComponent").forEach {
            assertFalse(it, DesignSystemMatching.isAllowed(m3 + it))
        }
        val entries = DesignSystemMatching.entries(
            "a/B.kt",
            listOf(m3 + "Text" to null, m3 + "SomeBrandNewComponent" to null),
            "import ${m3}Text\nText(); Text(); SomeBrandNewComponent()",
        )
        assertEquals(listOf("a/B.kt|Text|2", "a/B.kt|SomeBrandNewComponent|1"), entries)
    }

    @Test
    fun `a count increase in a baselined file fails`() {
        val baseline = setOf("a/B.kt|Text|2")
        val (same, _) = Baseline.diff(baseline, setOf("a/B.kt|Text|2"))
        assertTrue(same.isEmpty())
        val (new, fixed) = Baseline.diff(baseline, setOf("a/B.kt|Text|3"))
        assertEquals(setOf("a/B.kt|Text|3"), new)
        assertEquals(setOf("a/B.kt|Text|2"), fixed)
    }

    @Test
    fun `aliased and wildcard-free imports count the alias`() {
        val entries = DesignSystemMatching.entries("a/B.kt", listOf(m3 + "Text" to "M3Text"), "M3Text(); M3Text(); Text()")
        assertEquals(listOf("a/B.kt|Text|2"), entries)
    }

    @Test
    fun `wildcard imports are not resolved but fully qualified uses are`() {
        // Konsist reports `import androidx.compose.material3.*` as the name `androidx.compose.material3.*`.
        assertTrue(DesignSystemMatching.entries("a/B.kt", listOf(m3 + "*" to null), "Text()").size <= 1)
        val entries = DesignSystemMatching.entries(
            "a/B.kt",
            emptyList(),
            "val x = androidx.compose.material3.Text(\"hi\")\nandroidx.compose.material3.Text(\"a\")\nandroidx.compose.material3.MaterialTheme",
        )
        assertEquals(listOf("a/B.kt|Text|2"), entries)
    }

    @Test
    fun `comments and strings do not count`() {
        val text = "import ${m3}Text\n/** Text here */\n// Text\n/* Text /* Text */ Text */\nval s = \"Text\"\nText()"
        assertEquals(listOf("a/B.kt|Text|1"), DesignSystemMatching.entries("a/B.kt", listOf(m3 + "Text" to null), text))
    }

    @Test
    fun `adaptive components are flagged and helpers allowed`() {
        listOf("NavigationSuiteScaffold", "ListDetailPaneScaffold", "SupportingPaneScaffold", "AnimatedPane").forEach {
            assertFalse(it, DesignSystemMatching.isAllowed(m3 + "adaptive.layout.$it"))
            assertFalse(it, DesignSystemMatching.isAllowed(m3 + "adaptive.navigationsuite.$it"))
        }
        listOf(
            "adaptive.layout.PaneScaffoldDirective",
            "adaptive.layout.calculatePaneScaffoldDirective",
            "adaptive.navigation3.ListDetailSceneStrategy",
            "adaptive.currentWindowAdaptiveInfoV2",
        ).forEach { assertTrue(it, DesignSystemMatching.isAllowed(m3 + it)) }
    }

    @Test
    fun `count changes are reported with direction`() {
        val (down, _, _) = Baseline.splitCountChanges(setOf("a|Text|1"), setOf("a|Text|2"))
        assertEquals("a|Text: count went from 2 to 1; regenerate the baseline", down.single().message())
        val (up, newOnly, fixedOnly) = Baseline.splitCountChanges(setOf("a|Text|3", "b|Card|1"), setOf("a|Text|2"))
        assertEquals("a|Text: count went from 2 to 3; remove the new usage", up.single().message())
        assertEquals(setOf("b|Card|1"), newOnly)
        assertTrue(fixedOnly.isEmpty())
    }
}
