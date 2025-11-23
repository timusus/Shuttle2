package com.simplecityapps.shuttle.ui.screens.onboarding.mediaprovider.taglib

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simplecityapps.saf.DocumentNodeTree
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.snapshot.Snapshot
import com.simplecityapps.shuttle.ui.theme.ColorSchemePreviewParameterProvider

@Composable
fun DirectorySelectionDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DirectorySelectionViewModel = hiltViewModel()
) {
    val contentResolver = LocalContext.current.contentResolver
    val viewState by viewModel.viewState.collectAsStateWithLifecycle()
    val directoryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { safeUri -> viewModel.handleSafResult(contentResolver, safeUri) }
    }
    LaunchedEffect(Unit) {
        viewModel.onInitializeConfiguration()
    }
    DirectorySelectionDialog(
        modifier = modifier,
        viewState = viewState,
        onDoneClick = onDismissRequest,
        onDismissRequest = onDismissRequest,
        onRemoveDirectoryClick = viewModel::removeItem,
        onAddDirectoryClick = { directoryLauncher.launch(null) }
    )
}

@Composable
private fun DirectorySelectionDialog(
    viewState: DirectorySelectionViewState,
    onDoneClick: () -> Unit,
    onDismissRequest: () -> Unit,
    onAddDirectoryClick: () -> Unit,
    onRemoveDirectoryClick: (DirectorySelectionContract.Directory) -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        title = {
            Text(text = stringResource(R.string.onboarding_directories_dialog_add_title))
        },
        text = {
            AnimatedContent(targetState = viewState.directories) { directories ->
                if (directories.isEmpty()) {
                    EmptyDirectoryState()
                } else {
                    DirectoryList(
                        directories = viewState.directories,
                        onRemoveClick = onRemoveDirectoryClick
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onAddDirectoryClick) {
                Text(text = stringResource(R.string.onboarding_directories_dialog_add_button))
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDoneClick,
                enabled = viewState.isTraversalComplete
            ) {
                Text(text = stringResource(R.string.dialog_button_done))
            }
        }
    )
}

@Composable
private fun EmptyDirectoryState(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp)
    ) {
        Text(
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            text = stringResource(R.string.onboarding_directories_add_directories_help_text)
        )
    }
}

@Composable
private fun DirectoryList(
    directories: List<DirectorySelectionContract.Directory>,
    onRemoveClick: (DirectorySelectionContract.Directory) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.heightIn(max = 300.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items = directories) { directory ->
            DirectoryItem(
                directory = directory,
                onRemoveClick = { onRemoveClick(directory) }
            )
        }
    }
}

@Composable
private fun DirectoryItem(
    directory: DirectorySelectionContract.Directory,
    onRemoveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            contentDescription = null,
            imageVector = Icons.Outlined.Folder,
            tint = MaterialTheme.colorScheme.primary
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = directory.tree.displayName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                maxLines = 1,
                text = directory.tree.rootUri.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AnimatedVisibility(visible = !directory.traversalComplete) {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(8.dp)
                    .size(16.dp),
                strokeWidth = 2.dp
            )
        }

        IconButton(onClick = onRemoveClick) {
            Icon(
                contentDescription = null,
                imageVector = Icons.Default.Close,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Snapshot
@Preview
@Composable
private fun Empty(@PreviewParameter(ColorSchemePreviewParameterProvider::class) colorScheme: ColorScheme) {
    MaterialTheme(colorScheme = colorScheme) {
        DirectorySelectionDialog(
            onDismissRequest = {},
            viewState = DirectorySelectionViewState(
                directories = emptyList(),
                isTraversalComplete = true
            ),
            onAddDirectoryClick = {},
            onRemoveDirectoryClick = {},
            onDoneClick = {}
        )
    }
}

@Snapshot
@Preview
@Composable
private fun Success(@PreviewParameter(ColorSchemePreviewParameterProvider::class) colorScheme: ColorScheme) {
    MaterialTheme(colorScheme = colorScheme) {
        DirectorySelectionDialog(
            onDismissRequest = {},
            viewState = DirectorySelectionViewState(
                directories = listOf(
                    DirectorySelectionContract.Directory(
                        tree = DocumentNodeTree(
                            uri = Uri.EMPTY,
                            rootUri = "/my_music".toUri(),
                            documentId = "1",
                            displayName = "My Music",
                            mimeType = ".mp3"
                        ),
                        traversalComplete = false
                    ),
                    DirectorySelectionContract.Directory(
                        tree = DocumentNodeTree(
                            uri = Uri.EMPTY,
                            rootUri = "/my_audiobooks".toUri(),
                            documentId = "2",
                            displayName = "My Audiobooks",
                            mimeType = ".mp3"
                        ),
                        traversalComplete = true
                    )
                ),
                isTraversalComplete = true
            ),
            onAddDirectoryClick = {},
            onRemoveDirectoryClick = {},
            onDoneClick = {}
        )
    }
}
