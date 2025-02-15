package com.simplecityapps.shuttle.ui.screens.library.menu

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.toPersistentList

@Composable
fun Menu(
    items: PersistentList<MenuItem>,
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentItems by remember { mutableStateOf(items) }

    LaunchedEffect(expanded) {
        if (expanded) {
            currentItems = items
        }
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier
    ) {
        currentItems.forEach { item ->
            when (item) {
                is MenuItem.Item -> {
                    DropdownMenuItem(
                        text = { Text(text = item.title()) },
                        onClick = {
                            item.onClick()
                            onDismissRequest()
                        },
                        enabled = item.enabled
                    )
                }

                is MenuItem.Submenu -> {
                    DropdownMenuItem(
                        text = { Text(text = item.title()) },
                        onClick = {
                            currentItems = (listOf(MenuItem.Header(title = item.title)) + item.items).toPersistentList()
                        },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null
                            )
                        }
                    )
                }

                is MenuItem.Header -> {
                    ProvideTextStyle(MaterialTheme.typography.labelLarge) {
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .heightIn(min = 40.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = item.title(),
                                style = MaterialTheme.typography.labelLarge.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
