package com.simplecityapps.shuttle.ui.common.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow

/**
 * Simple detail screen scaffold with a pinned small top app bar.
 *
 * Content is a single LazyColumn. The hero image, metadata, action buttons,
 * and track list are all regular list items that scroll naturally.
 * The top app bar stays pinned with back navigation and overflow actions, and shows [title] and
 * [subtitle] once the item at [headerItemIndex] (the one carrying the page title) has scrolled
 * under it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScaffold(
    title: String,
    subtitle: String?,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    headerItemIndex: Int = 0,
    actions: @Composable RowScope.() -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val collapsed by remember(listState, headerItemIndex) {
        derivedStateOf { listState.isScrolledPast(headerItemIndex) }
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    AnimatedVisibility(
                        visible = collapsed,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        DetailTopBarTitle(title = title, subtitle = subtitle)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate up",
                        )
                    }
                },
                actions = actions,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = innerPadding,
        ) {
            content()
        }
    }
}

@Composable
private fun DetailTopBarTitle(
    title: String,
    subtitle: String?,
) {
    Column(modifier = Modifier.testTag("detail-top-bar-title")) {
        Text(
            text = title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * True once the item at [index] has scrolled entirely past the top of the list's content area,
 * which (with the top bar's height applied as content padding) is the bottom edge of the bar.
 */
private fun LazyListState.isScrolledPast(index: Int): Boolean {
    if (firstVisibleItemIndex > index) return true
    val item = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return false
    return item.offset + item.size <= 0
}
