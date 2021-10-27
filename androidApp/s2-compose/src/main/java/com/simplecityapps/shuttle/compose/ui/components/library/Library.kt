package com.simplecityapps.shuttle.compose.ui.components.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.pagerTabIndicatorOffset
import com.google.accompanist.pager.rememberPagerState
import com.simplecityapps.shuttle.compose.ui.components.ThemedPreviewProvider
import com.simplecityapps.shuttle.compose.ui.theme.MaterialColors
import com.simplecityapps.shuttle.compose.ui.theme.Theme
import com.simplecityapps.shuttle.ui.library.GenreListViewModel

@OptIn(ExperimentalPagerApi::class)
@Composable
fun Library() {

    val pagerState = rememberPagerState(pageCount = LibraryTab.values().size)

    Scaffold(topBar = {
        Column(Modifier) {
            TabRow(
                selectedTabIndex = pagerState.currentPage,
                indicator = { tabPositions ->
                    TabRowDefaults.Indicator(
                        Modifier.pagerTabIndicatorOffset(pagerState, tabPositions)
                    )
                },
                backgroundColor = MaterialColors.background
            ) {
                LibraryTab.values().mapIndexed { index, tab ->
                    Tab(
                        modifier = Modifier.height(48.dp),
                        selected = pagerState.currentPage == index,
                        onClick = {},
                        selectedContentColor = MaterialColors.onBackground,
                        unselectedContentColor = MaterialColors.onBackground.copy(alpha = ContentAlpha.medium)
                    ) {
                        Text(
                            text = stringResource(id = tab.nameResId()),
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }) {
        HorizontalPager(state = pagerState) { page ->
            when (val page = LibraryTab.values()[page]) {
                LibraryTab.Genres -> {
                    GenreList(hiltViewModel() as GenreListViewModel)
                }
                LibraryTab.Playlists -> {

                }
                LibraryTab.Artists -> {

                }
                LibraryTab.Albums -> {

                }
                LibraryTab.Songs -> {

                }
            }
            Box(
                Modifier
                    .fillMaxSize()
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LibraryPreview(@PreviewParameter(ThemedPreviewProvider::class) darkTheme: Boolean) {
    Theme(isDark = darkTheme) {
        Library()
    }
}