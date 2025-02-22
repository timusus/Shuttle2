package com.simplecityapps.shuttle.compose.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeekSheetScaffold(
    peekContent: @Composable () -> Unit,
    sheetContent: @Composable () -> Unit,
    content: @Composable () -> Unit,
    bottomBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.PartiallyExpanded
    )
    Scaffold(
        modifier = modifier,
        bottomBar = {
            AnimatedVisibility(
                visible = sheetState.targetValue != SheetValue.Expanded,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(durationMillis = 300)
                ),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(durationMillis = 300)
                )
            ) {
                bottomBar()
            }
        }
    ) { paddingValues ->
        var peekHeight by remember { mutableIntStateOf(0) }
        val coroutineScope = rememberCoroutineScope()

        BottomSheetScaffold(
            modifier = Modifier.padding(paddingValues),
            scaffoldState = rememberBottomSheetScaffoldState(
                bottomSheetState = sheetState
            ),
            sheetPeekHeight = with(LocalDensity.current) { peekHeight.toDp() } + paddingValues.calculateBottomPadding(),
            sheetContainerColor = MaterialTheme.colorScheme.background,
            sheetContent = {
                Box {
                    sheetContent()
                    androidx.compose.animation.AnimatedVisibility(
                        visible = sheetState.targetValue != SheetValue.Expanded,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Surface(
                            modifier = Modifier
                                .onGloballyPositioned {
                                    peekHeight = it.size.height
                                },
                            onClick = {
                                coroutineScope.launch {
                                    sheetState.expand()
                                }
                            },
                            color = MaterialTheme.colorScheme.surfaceContainer,
                        ) {
                            Column {
                                peekContent()
                                HorizontalDivider()
                            }
                        }
                    }
                }
            },
            sheetDragHandle = null,
        ) {
            content()
        }
    }
}
