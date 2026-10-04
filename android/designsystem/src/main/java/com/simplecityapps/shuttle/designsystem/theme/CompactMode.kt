package com.simplecityapps.shuttle.designsystem.theme

import androidx.compose.runtime.compositionLocalOf

/**
 * Whether the UI lays out compact: smaller rows, artwork and headers across Home, Library and
 * Settings. Off by default; the Appearance setting flips it live, and every reader below
 * recomposes on change. Density only — it never changes icons, placeholders or navigation shape;
 * those stay separate design decisions.
 */
val LocalCompactMode = compositionLocalOf { false }
