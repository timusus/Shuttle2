package com.simplecityapps.shuttle.ui.shell

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The shell's one snackbar host, shown above the mini player (app-shell.md, section 4). Destinations post to it
 * rather than owning a host each, so a snackbar survives navigating away from the screen that raised it.
 */
val LocalShellSnackbarHostState = staticCompositionLocalOf { SnackbarHostState() }
