package com.simplecityapps.shuttle.compose.ui.main.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simplecityapps.shuttle.compose.ui.theme.MaterialColors
import com.simplecityapps.shuttle.compose.ui.theme.S2androidTheme

@Composable
fun Library() {

    Scaffold(topBar = {
        Column {
            var selectedTabIndex by remember { mutableStateOf(0) }
            TabRow(
                selectedTabIndex = selectedTabIndex,
                backgroundColor = MaterialColors.background
            ) {
                LibraryTab.values().mapIndexed { index, tab ->
                    Tab(
                        modifier = Modifier.height(48.dp),
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        selectedContentColor = MaterialColors.onBackground,
                        unselectedContentColor = MaterialColors.onBackground.copy(alpha = ContentAlpha.medium)
                    ) {
                        Text(
                            text = stringResource(id = tab.nameResId()),
                            fontSize = if (selectedTabIndex == index) 14.sp else 12.sp
                        )
                    }
                }
            }
        }
    }) {

    }

}

@Preview(showBackground = true)
@Composable
fun LibraryPreview() {
    S2androidTheme {
        Library()
    }
}

