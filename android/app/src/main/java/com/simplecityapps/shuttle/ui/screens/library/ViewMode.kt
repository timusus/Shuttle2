package com.simplecityapps.shuttle.ui.screens.library

enum class ViewMode {
    List,
    Grid
}

fun String?.toViewMode(): ViewMode = ViewMode.valueOf(this ?: ViewMode.List.name)
