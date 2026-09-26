package com.simplecityapps.shuttle.debug.livelog

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.EmptyState
import com.simplecityapps.shuttle.designsystem.component.S2IconButton
import com.simplecityapps.shuttle.designsystem.component.S2TopBar

/** DebugLoggingTree's recent output, live: newest at the bottom, auto-scrolling as lines arrive. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveLogScreen(
    uiState: LiveLogUiState,
    onNavigateUp: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    LaunchedEffect(uiState.lines.size) {
        if (uiState.lines.isNotEmpty()) listState.animateScrollToItem(uiState.lines.lastIndex)
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            S2TopBar(
                title = stringResource(R.string.pref_view_live_log_title),
                onBack = onNavigateUp,
                actions = {
                    S2IconButton(
                        icon = Icons.Rounded.ContentCopy,
                        contentDescription = stringResource(R.string.live_log_copy),
                        onClick = { copyToClipboard(context, uiState.lines) }
                    )
                    S2IconButton(
                        icon = Icons.Rounded.DeleteSweep,
                        contentDescription = stringResource(R.string.live_log_clear),
                        onClick = onClear
                    )
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        if (uiState.lines.isEmpty()) {
            EmptyState(title = stringResource(R.string.live_log_empty), icon = Icons.Rounded.Info, modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                items(uiState.lines, key = { it.id }) { line ->
                    Text(
                        text = line.format(),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = line.priority.color()
                    )
                }
            }
        }
    }
}

@Composable
private fun Int.color(): Color = when (this) {
    Log.ERROR, Log.ASSERT -> MaterialTheme.colorScheme.error
    Log.WARN -> MaterialTheme.colorScheme.tertiary
    Log.DEBUG, Log.VERBOSE -> MaterialTheme.colorScheme.onSurfaceVariant
    else -> MaterialTheme.colorScheme.onSurface
}

private fun copyToClipboard(
    context: Context,
    lines: List<LiveLogLine>
) {
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboardManager.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.settings_logging_clipboard_name), lines.formatAll()))
}
