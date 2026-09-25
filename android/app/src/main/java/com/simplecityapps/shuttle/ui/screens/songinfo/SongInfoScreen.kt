package com.simplecityapps.shuttle.ui.screens.songinfo

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.ArtworkPlaceholder
import com.simplecityapps.shuttle.designsystem.component.EmptyState
import com.simplecityapps.shuttle.designsystem.component.LoadingState
import com.simplecityapps.shuttle.designsystem.component.S2IconButton
import com.simplecityapps.shuttle.designsystem.component.S2TopBar
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.screens.library.LibraryArtwork
import com.simplecityapps.shuttle.ui.screens.tageditor.SongInfoRoute
import com.simplecityapps.shuttle.ui.shell.LocalShellSnackbarHostState
import kotlinx.coroutines.launch

@Composable
fun SongInfoDestination(
    route: SongInfoRoute,
    onNavigateUp: () -> Unit,
) {
    val viewModel = hiltViewModel<SongInfoViewModel, SongInfoViewModel.Factory> { it.create(route.songId) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = LocalShellSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val copied = stringResource(R.string.song_info_path_copied)
    SongInfoScreen(
        uiState = uiState,
        onNavigateUp = onNavigateUp,
        onCopyPath = { path ->
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(path, path))
            // Android 13 and up confirms a copy itself.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) scope.launch { snackbarHostState.showSnackbar(copied) }
        },
    )
}

/** Song info (inventory §5): every detail of one song's file and tags, with its path to copy. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongInfoScreen(
    uiState: SongInfoUiState,
    onNavigateUp: () -> Unit,
    onCopyPath: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val song = uiState.song
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection).testTag("song-info"),
        topBar = {
            S2TopBar(
                title = stringResource(R.string.song_info_dialog_title),
                onBack = onNavigateUp,
                scrollBehavior = scrollBehavior,
                actions = {
                    if (song != null) {
                        S2IconButton(icon = Icons.Rounded.ContentCopy, contentDescription = stringResource(R.string.song_info_copy_path), onClick = { onCopyPath(song.displayPath) })
                    }
                },
            )
        },
    ) { padding ->
        when {
            song != null -> SongInfoContent(song, Modifier.padding(padding))
            uiState.loading -> LoadingState(Modifier.padding(padding).padding(vertical = 48.dp))
            else -> EmptyState(title = stringResource(R.string.song_info_not_found), modifier = Modifier.padding(padding).padding(vertical = 48.dp))
        }
    }
}

@Composable
private fun SongInfoContent(
    song: Song,
    modifier: Modifier = Modifier,
) {
    val unknown = stringResource(R.string.song_info_unknown)
    val rows = song.infoRows()
    SelectionContainer(modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().testTag("song-info-list")) {
            item(key = "header") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LibraryArtwork(song, ArtworkPlaceholder.Song)
                    Column(Modifier.weight(1f)) {
                        Text(song.name ?: unknown, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        song.friendlyArtistName?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            items(rows, key = { it.label }) { row ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(stringResource(row.label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(row.value?.takeIf { it.isNotBlank() } ?: unknown, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
