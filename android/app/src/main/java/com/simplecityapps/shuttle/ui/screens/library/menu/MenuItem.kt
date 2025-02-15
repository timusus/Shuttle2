package com.simplecityapps.shuttle.ui.screens.library.menu

import androidx.compose.runtime.Composable

sealed class MenuItem {
    data class Item(
        val title: @Composable () -> String,
        val enabled: Boolean = true,
        val onClick: () -> Unit
    ) : MenuItem()

    data class Submenu(
        val title: @Composable () -> String,
        val items: List<MenuItem>
    ) : MenuItem()

    data class Header(
        val title: @Composable () -> String
    ) : MenuItem()
}
