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
}
