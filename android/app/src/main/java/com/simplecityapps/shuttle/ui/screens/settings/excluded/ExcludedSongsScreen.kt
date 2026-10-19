package com.simplecityapps.shuttle.ui.screens.settings.excluded

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.EmptyState
import com.simplecityapps.shuttle.designsystem.component.LoadingState
import com.simplecityapps.shuttle.designsystem.component.S2Action
import com.simplecityapps.shuttle.designsystem.component.S2Dialog
import com.simplecityapps.shuttle.designsystem.component.S2IconButton
import com.simplecityapps.shuttle.designsystem.component.S2Menu
import com.simplecityapps.shuttle.designsystem.component.SongRow
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.common.ConfirmEvent
import com.simplecityapps.shuttle.ui.common.onConfirmEvent
import com.simplecityapps.shuttle.ui.screens.settings.SettingsScaffold

/** The excluded songs; each row's menu puts its song back in the library, and the top bar puts them all back. */
@Composable
fun ExcludedSongsScreen(
    uiState: ExcludedSongsUiState,
    onNavigateUp: () -> Unit,
    onInclude: (Song) -> Unit,
    onIncludeAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewState = excludedSongsViewState(uiState)
    val onEvent = ExcludedSongsCallbacks(onNavigateUp, onInclude, onIncludeAll)::onEvent
    var confirmIncludeAll by rememberSaveable { mutableStateOf(false) }
    val confirm = { event: ConfirmEvent ->
        val step = onConfirmEvent(confirmIncludeAll, event)
        confirmIncludeAll = step.showing
        if (step.confirmed) onEvent(ExcludedSongsEvent.IncludeAll)
    }
    var menuSongId by rememberSaveable { mutableStateOf<Long?>(null) }
    val menu = { event: SongMenuEvent ->
        val step = onSongMenuEvent(event)
        menuSongId = step.openSongId
        step.event?.let(onEvent)
        Unit
    }

    SettingsScaffold(
        title = stringResource(R.string.pref_exclude_title),
        onNavigateUp = { onEvent(ExcludedSongsEvent.NavigateUp) },
        modifier = modifier,
        contentPadding = PaddingValues(),
        verticalArrangement = Arrangement.Top,
        actions = {
            if (viewState.showIncludeAll) {
                S2IconButton(
                    icon = Icons.Rounded.ClearAll,
                    contentDescription = stringResource(R.string.excluded_songs_include_all),
                    onClick = { confirm(ConfirmEvent.Open) }
                )
            }
        }
    ) {
        when (val content = viewState.content) {
            ExcludedSongsViewState.Content.Loading -> item(key = "loading") { LoadingState() }

            ExcludedSongsViewState.Content.Empty -> item(key = "empty") {
                EmptyState(title = stringResource(R.string.dialog_exclude_list_empty), icon = Icons.Rounded.Block)
            }

            is ExcludedSongsViewState.Content.Songs -> items(content.rows, key = { it.song.id }) { row ->
                val song = row.song
                val openMenu = { menu(SongMenuEvent.Open(song)) }
                Box {
                    SongRow(
                        title = row.title,
                        subtitle = row.subtitle,
                        onClick = openMenu,
                        onMore = openMenu
                    )
                    S2Menu(
                        expanded = menuSongId == song.id,
                        onDismissRequest = { menu(SongMenuEvent.Dismiss) },
                        groups = listOf(
                            listOf(
                                S2Action(
                                    label = stringResource(R.string.excluded_songs_include),
                                    icon = Icons.Rounded.Restore,
                                    onClick = { menu(SongMenuEvent.Include(song)) }
                                )
                            )
                        )
                    )
                }
            }
        }
    }

    if (confirmIncludeAll) {
        S2Dialog(
            title = stringResource(R.string.excluded_songs_include_all_title),
            onDismissRequest = { confirm(ConfirmEvent.Dismiss) },
            confirmLabel = stringResource(R.string.excluded_songs_include_all),
            onConfirm = { confirm(ConfirmEvent.Confirm) },
            dismissLabel = stringResource(android.R.string.cancel)
        ) {
            Text(stringResource(R.string.excluded_songs_include_all_message))
        }
    }
}
