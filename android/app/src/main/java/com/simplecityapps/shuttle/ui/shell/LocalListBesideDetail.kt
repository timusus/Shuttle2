package com.simplecityapps.shuttle.ui.shell

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * True where list-detail scenes have room for two panes, so a list shows beside its detail. A list pane reads it to
 * mark the row whose page is open beside it; in one pane there is nothing beside the list to mark.
 */
val LocalListBesideDetail = staticCompositionLocalOf { false }
