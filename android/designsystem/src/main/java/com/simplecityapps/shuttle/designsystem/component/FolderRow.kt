package com.simplecityapps.shuttle.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import com.simplecityapps.shuttle.designsystem.preview.S2Preview

/** What a [FolderRow] stands for: a folder to open, or a song file to play. */
enum class FolderEntryKind { Folder, File }

/**
 * An entry in the folder browser. A folder shows the folder placeholder and its [summary] (item
 * count); a file shows its [artwork] (the song placeholder when there is none) and [meta] (duration).
 */
@Composable
fun FolderRow(
    name: String,
    kind: FolderEntryKind,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    meta: String? = null,
    artwork: (@Composable () -> Unit)? = null,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onMore: (() -> Unit)? = null,
) {
    MediaRow(
        title = AnnotatedString(name),
        onClick = onClick,
        modifier = modifier,
        supporting = summary?.let(::AnnotatedString),
        meta = meta,
        leading = artwork ?: {
            Artwork(if (kind == FolderEntryKind.Folder) ArtworkPlaceholder.Folder else ArtworkPlaceholder.Song)
        },
        selected = selected,
        onLongClick = onLongClick,
        onMore = onMore,
    )
}

@Preview
@Composable
private fun FolderRowPreview() {
    S2Preview {
        FolderRow(name = "Music", kind = FolderEntryKind.Folder, onClick = {}, summary = "12 folders · 3 songs", onMore = {})
    }
}
