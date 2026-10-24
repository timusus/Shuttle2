package com.simplecityapps.shuttle.ui.shell.player

import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.S2Text
import com.simplecityapps.shuttle.ui.screens.sources.titleRes

@Composable
internal fun LoadFailure.message(): String = when (this) {
    is LoadFailure.ServerUnreachable -> stringResource(R.string.player_cant_reach_source, stringResource(provider.titleRes))
    LoadFailure.CantPlay -> stringResource(R.string.player_cant_play_song)
}

/** Why the current song can't play; for an unreachable server it opens Sources, where the server can be checked. */
@Composable
internal fun LoadFailureMessage(
    failure: LoadFailure,
    onOpenSources: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val open = if (failure is LoadFailure.ServerUnreachable) Modifier.clickable(role = Role.Button, onClick = onOpenSources) else Modifier
    S2Text(
        text = failure.message(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.error,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.then(open)
    )
}
