package com.simplecityapps.shuttle.ui.screens.songinfo

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItemShapes
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.ArtworkPlaceholder
import com.simplecityapps.shuttle.designsystem.component.ArtworkSize
import com.simplecityapps.shuttle.designsystem.component.EmptyState
import com.simplecityapps.shuttle.designsystem.component.InfoSetting
import com.simplecityapps.shuttle.designsystem.component.LoadingState
import com.simplecityapps.shuttle.designsystem.component.S2IconButton
import com.simplecityapps.shuttle.designsystem.component.S2InfoChip
import com.simplecityapps.shuttle.designsystem.component.S2TopBar
import com.simplecityapps.shuttle.designsystem.component.SettingsGroup
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.screens.library.LibraryArtwork
import com.simplecityapps.shuttle.ui.screens.tageditor.SongInfoRoute
import com.simplecityapps.shuttle.ui.shell.LocalInShellSheet
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

/**
 * Song info (inventory §5): the song's artwork and headline file facts, then every tag and file detail in cards, with its
 * path to copy. In the phone's sheet it has no back arrow: a swipe, a tap outside or back dismisses the sheet.
 */
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
                onBack = onNavigateUp.takeUnless { LocalInShellSheet.current },
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
    val sections = song.infoSections()
    SelectionContainer(modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("song-info-list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "header") { SongInfoHero(song, unknown) }
            items(sections, key = { it.title }) { section ->
                SettingsGroup(
                    title = stringResource(section.title),
                    rows = section.rows.map { row ->
                        { shapes: ListItemShapes ->
                            InfoSetting(title = stringResource(row.label), summary = row.value?.takeIf { it.isNotBlank() } ?: unknown, shapes = shapes)
                        }
                    },
                )
            }
        }
    }
}

/** The artwork, title and artist, over the file's format, bit rate and sample rate as chips. The artwork is smaller in the sheet, so its half height shows them. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SongInfoHero(
    song: Song,
    unknown: String,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LibraryArtwork(song, ArtworkPlaceholder.Song, size = if (LocalInShellSheet.current) ArtworkSize.Grid else ArtworkSize.Hero, modifier = Modifier.padding(bottom = 12.dp))
        Text(song.name ?: unknown, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        song.friendlyArtistName?.let {
            Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val chips = song.infoChips()
        if (chips.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                chips.forEach { S2InfoChip(it) }
            }
        }
    }
}
