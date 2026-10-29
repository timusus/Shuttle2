package com.simplecityapps.shuttle.ui.screens.library

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.S2Action
import com.simplecityapps.shuttle.designsystem.component.S2ActionsSheet
import com.simplecityapps.shuttle.model.AlbumSources
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.ui.screens.sources.ProviderMark
import com.simplecityapps.shuttle.ui.screens.sources.titleRes

/** "Primary source": Automatic first, then each source with how much of the album it holds, the current choice ticked. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrimarySourceSheet(
    sources: AlbumSources,
    onPick: (MediaProviderType?) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val resources = LocalResources.current
    S2ActionsSheet(
        title = stringResource(R.string.album_primary_source),
        actions = listOf(
            S2Action(
                label = stringResource(R.string.album_primary_source_automatic),
                onClick = { onPick(null) },
                selected = sources.automatic,
            ),
        ) + sources.sources.map { source ->
            S2Action(
                label = stringResource(source.provider.titleRes),
                onClick = { onPick(source.provider) },
                selected = source.primary,
                supporting = resources.getQuantityString(R.plurals.album_primary_source_songs, sources.songCount, source.songCount, sources.songCount),
                leading = { ProviderMark(source.provider) },
            )
        },
        onDismissRequest = onDismissRequest,
    )
}
