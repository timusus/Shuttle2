package com.simplecityapps.shuttle.ui.screens.library.menu

import androidx.compose.runtime.Composable
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.toPersistentList

@DslMarker
annotation class MenuDsl

@MenuDsl
class MenuBuilder {
    private val _items = mutableListOf<MenuItem>()
    val items: PersistentList<MenuItem>
        get() = _items.toPersistentList()

    fun action(
        title: @Composable () -> String,
        enabled: Boolean = true,
        onClick: () -> Unit
    ) {
        _items += MenuItem.Item(
            title = title,
            onClick = onClick,
            enabled = enabled
        )
    }

    fun submenu(
        title: @Composable () -> String,
        block: MenuBuilder.() -> Unit
    ) {
        val builder = MenuBuilder().apply(block)
        _items += MenuItem.Submenu(
            title = title,
            items = builder.items
        )
    }
}

@Composable
fun menu(block: @Composable MenuBuilder.() -> Unit): PersistentList<MenuItem> {
    val builder = MenuBuilder()
    builder.block()
    return builder.items
}
