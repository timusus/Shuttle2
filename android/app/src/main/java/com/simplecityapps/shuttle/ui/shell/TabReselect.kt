package com.simplecityapps.shuttle.ui.shell

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * The tabs the user re-selected while already at their root, as the shell provides them. A tab's root screen collects
 * them with [ScrollToTopOnReselect]; a screen that should not react (a Library page that isn't the visible one)
 * is given an empty flow instead.
 */
val LocalTabReselects = compositionLocalOf<Flow<ShellTab>> { emptyFlow() }

/** Scrolls [state] to its first item when [tab] is re-selected at its root. */
@Composable
fun ScrollToTopOnReselect(tab: ShellTab, state: LazyListState) {
    ScrollToTopOnReselect(tab) { state.animateScrollToItem(0) }
}

/** Scrolls [state] to its first item when [tab] is re-selected at its root. */
@Composable
fun ScrollToTopOnReselect(tab: ShellTab, state: LazyGridState) {
    ScrollToTopOnReselect(tab) { state.animateScrollToItem(0) }
}

@Composable
private fun ScrollToTopOnReselect(tab: ShellTab, scrollToTop: suspend () -> Unit) {
    val reselects = LocalTabReselects.current
    val currentScrollToTop by rememberUpdatedState(scrollToTop)
    LaunchedEffect(reselects, tab) {
        reselects.collect { if (it == tab) currentScrollToTop() }
    }
}
