package com.simplecityapps.shuttle.designsystem.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.designsystem.component.EmptyState
import com.simplecityapps.shuttle.designsystem.component.ErrorState
import com.simplecityapps.shuttle.designsystem.component.InlineLoadingIndicator
import com.simplecityapps.shuttle.designsystem.component.LoadingState
import com.simplecityapps.shuttle.designsystem.component.StateAction

@Composable
fun EmptyStateBoard(width: BoardWidth) {
    Board(
        width,
        listOf(
            BoardSection("With action") {
                EmptyState(
                    title = "No songs yet",
                    message = "Add a music folder to start building your library.",
                    action = StateAction("Add a folder") {},
                )
            },
            BoardSection("Without action") {
                EmptyState(title = "This playlist is empty", message = "Add songs from their menu.", icon = Icons.AutoMirrored.Rounded.QueueMusic)
            },
        ),
    )
}

@Composable
fun LoadingStateBoard(width: BoardWidth) {
    Board(
        width,
        listOf(
            BoardSection("Full screen, indeterminate") { LoadingState() },
            BoardSection("Determinate (import progress)") { LoadingState(message = "Importing 1,204 of 3,310 songs", progress = { 0.36f }) },
            BoardSection("Inline and pull to refresh: indeterminate, determinate") {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    InlineLoadingIndicator()
                    InlineLoadingIndicator(progress = { 0.7f })
                }
            },
        ),
    )
}

@Composable
fun ErrorStateBoard(width: BoardWidth) {
    Board(
        width,
        listOf(
            BoardSection("Retry") {
                ErrorState(
                    title = "Couldn't load your library",
                    message = "Something went wrong reading the music folder.",
                    action = StateAction("Retry") {},
                )
            },
            BoardSection("Provider sign-in") {
                ErrorState(
                    title = "Signed out of Jellyfin",
                    message = "Sign in again to keep streaming from your server.",
                    icon = Icons.Rounded.CloudOff,
                    action = StateAction("Sign in") {},
                )
            },
        ),
    )
}
