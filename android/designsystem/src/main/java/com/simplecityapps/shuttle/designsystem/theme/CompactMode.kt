package com.simplecityapps.shuttle.designsystem.theme

import androidx.compose.runtime.compositionLocalOf

/**
 * Whether the UI lays out compact: smaller row artwork, shorter section headers, narrower home
 * tiles, tighter settings groups, a slimmer mini-player, and pinned one-row bars in place of the
 * large collapsing ones. Off by default; the Appearance setting flips it live, and every reader
 * below recomposes on change.
 */
val LocalCompactMode = compositionLocalOf { false }
