package com.simplecityapps.shuttle.designsystem.theme

import androidx.compose.runtime.compositionLocalOf

/**
 * Whether the UI lays out compact — the "classic" density: small row artwork, flat tiles with no
 * cards, an icon-only nav bar, short section headers and pinned one-row bars in place of the
 * large collapsing ones. Off by default; the Appearance setting flips it live, and every reader
 * below recomposes on change.
 */
val LocalCompactMode = compositionLocalOf { false }
