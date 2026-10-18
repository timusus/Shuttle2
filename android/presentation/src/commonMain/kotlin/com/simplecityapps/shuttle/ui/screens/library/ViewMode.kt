package com.simplecityapps.shuttle.ui.screens.library

/** A library list's layout. The names are persisted, so renaming one resets users who chose it to the default. */
enum class ViewMode {
    List,
    Grid,

    CompactGrid,
}

/** The stored [ViewMode], or [default] when none is stored or the stored name is no longer a mode. */
fun String?.toViewMode(default: ViewMode = ViewMode.List): ViewMode = ViewMode.entries.firstOrNull { it.name == this } ?: default
