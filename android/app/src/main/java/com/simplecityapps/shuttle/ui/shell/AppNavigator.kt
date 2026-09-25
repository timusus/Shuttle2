package com.simplecityapps.shuttle.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack

/**
 * One back stack per top-level tab plus the selected tab (docs/architecture/app-shell.md,
 * "Back stacks and the navigator"). The display shows the start tab's stack followed by the
 * selected tab's, so back at the root of another tab returns to the start tab; re-selecting a
 * tab restores its stack, and re-selecting the current tab pops it to its root.
 *
 * Plain list code over snapshot state, so the rules are unit tested without Compose UI.
 */
@Stable
class AppNavigator(
    val startTab: ShellTab,
    private val stacks: Map<ShellTab, MutableList<NavKey>>,
    selectedTab: MutableState<ShellTab>,
) {
    var selectedTab: ShellTab by selectedTab
        private set

    init {
        require(ShellTab.entries.all { stacks[it]?.firstOrNull() == it.root }) { "Every tab's stack must start at its root" }
    }

    fun stack(tab: ShellTab): List<NavKey> = stacks.getValue(tab)

    /** The tabs whose stacks the display shows, in order: the start tab, then the selected tab. */
    val visibleTabs: List<ShellTab>
        get() = if (selectedTab == startTab) listOf(startTab) else listOf(startTab, selectedTab)

    /** Pushes [route] onto the selected tab's stack. */
    fun open(route: NavKey) {
        stacks.getValue(selectedTab).add(route)
    }

    fun selectTab(tab: ShellTab) {
        if (tab == selectedTab) {
            val stack = stacks.getValue(tab)
            while (stack.size > 1) stack.removeAt(stack.lastIndex)
        } else {
            selectedTab = tab
        }
    }

    /** Pops one entry. Returns false when there is nothing left to pop, so the activity should finish. */
    fun back(): Boolean {
        val stack = stacks.getValue(selectedTab)
        return when {
            stack.size > 1 -> {
                stack.removeAt(stack.lastIndex)
                true
            }

            selectedTab != startTab -> {
                selectedTab = startTab
                true
            }

            else -> false
        }
    }
}

@Composable
fun rememberAppNavigator(startTab: ShellTab): AppNavigator {
    // ShellTab.entries is fixed, so these calls keep their order across recompositions, and each
    // returns the same saved stack instance every time.
    val stacks = ShellTab.entries.associateWith { tab -> rememberNavBackStack(tab.root) }
    val selectedTab = rememberSaveable { mutableStateOf(startTab) }
    return remember(startTab) { AppNavigator(startTab, stacks, selectedTab) }
}
