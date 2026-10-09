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
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * One back stack per top-level tab plus the selected tab (docs/architecture/app-shell.md,
 * "Back stacks and the navigator"). The display shows the start tab's stack followed by the
 * selected tab's, so back at the root of another tab returns to the start tab; re-selecting a
 * tab restores its stack, and re-selecting the current tab pops it to its root, or, already there, announces it on
 * [reselects] for the tab's screen to scroll to the top. Leaving a tab
 * dismisses any utility destinations ([UtilityRoute]) open on it, so coming back shows the tab's
 * own screens with the navigation in place.
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

    private val _reselects = MutableSharedFlow<ShellTab>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** The tab re-selected while at its root; nothing replays, so a tab with no screen listening drops it. */
    val reselects: Flow<ShellTab> = _reselects

    fun stack(tab: ShellTab): List<NavKey> = stacks.getValue(tab)

    /** The tabs whose stacks the display shows, in order: the start tab, then the selected tab. */
    val visibleTabs: List<ShellTab>
        get() = if (selectedTab == startTab) listOf(startTab) else listOf(startTab, selectedTab)

    /** False while the selected tab's top route is a [UtilityRoute], which shows without the navigation bar or rail. */
    val showsNavigation: Boolean
        get() = stack(selectedTab).last() !is UtilityRoute

    /** Pushes [route] onto the selected tab's stack. */
    fun open(route: NavKey) {
        stacks.getValue(selectedTab).add(route)
    }

    /**
     * Drops whatever sits above the top-most [anchor] on the selected tab's stack and pushes [route], as picking a page
     * in a list with another page open beside it does. The anchor stays, so the stack keeps its root; with no anchor on
     * the stack this is [open].
     */
    fun replaceAbove(anchor: NavKey, route: NavKey) {
        val stack = stacks.getValue(selectedTab)
        val index = stack.lastIndexOf(anchor)
        if (index >= 0 && stack.size == index + 2 && stack.last() == route) return
        if (index >= 0) while (stack.size > index + 1) stack.removeAt(stack.lastIndex)
        stack.add(route)
    }

    fun selectTab(tab: ShellTab) {
        if (tab == selectedTab) {
            val stack = stacks.getValue(tab)
            if (stack.size > 1) {
                while (stack.size > 1) stack.removeAt(stack.lastIndex)
            } else {
                _reselects.tryEmit(tab)
            }
        } else {
            leave(selectedTab)
            selectedTab = tab
        }
    }

    /** Shows [request]'s tab at its root, with the requested route on top; the tab's earlier screens would only sit between. */
    fun show(request: ShellRequest) {
        if (request.tab != selectedTab) {
            selectTab(request.tab)
        }
        val stack = stacks.getValue(request.tab)
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
        request.route?.let { stack.add(it) }
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
                leave(selectedTab)
                selectedTab = startTab
                true
            }

            else -> false
        }
    }

    /** Pops [tab]'s stack from its first [UtilityRoute] up, dropping the utility destinations and anything opened from them. */
    private fun leave(tab: ShellTab) {
        val stack = stacks.getValue(tab)
        val first = stack.indexOfFirst { it is UtilityRoute }
        if (first > 0) while (stack.size > first) stack.removeAt(stack.lastIndex)
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
