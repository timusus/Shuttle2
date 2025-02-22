package com.simplecityapps.shuttle.compose.ui.features.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.simplecityapps.shuttle.compose.ui.components.PeekSheetScaffold
import com.simplecityapps.shuttle.compose.ui.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContent(
    modifier: Modifier = Modifier,
) {
    PeekSheetScaffold(
        modifier = modifier,
        peekContent = {
            MiniPlayer()
        },
        sheetContent = {
            SheetContent()
        },
        bottomBar = {
            BottomBar()
        },
        content = {
            Scaffold(
                modifier = Modifier.consumeWindowInsets(WindowInsets.statusBars),
                topBar = {
                    TopAppBar(
                        title = {
                            Text(text = "Library")
                        }
                    )
                },
            ) { paddingValues ->
                Box(
                    Modifier
                        .padding(paddingValues)
                ) {

                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SheetContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
    ) {
        TopAppBar(
            title = {
                Text(
                    text = "Now Playing"
                )
            },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ContentPreview() {
    AppTheme {
        MainContent()
    }
}
