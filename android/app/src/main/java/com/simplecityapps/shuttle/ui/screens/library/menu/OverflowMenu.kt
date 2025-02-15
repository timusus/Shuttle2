package com.simplecityapps.shuttle.ui.screens.library.menu

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.PersistentList

@Composable
fun OverflowMenu(
    menuItems: PersistentList<MenuItem>,
    modifier: Modifier = Modifier
) {
    var isVisible by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        IconButton(
            onClick = {
                isVisible = true
            }
        ) {
            Icon(
                modifier = Modifier.size(16.dp),
                imageVector = Icons.Default.MoreVert,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
        Menu(
            expanded = isVisible,
            items = menuItems,
            onDismissRequest = { isVisible = false }
        )
    }
}
