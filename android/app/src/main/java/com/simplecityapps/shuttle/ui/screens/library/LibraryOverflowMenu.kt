package com.simplecityapps.shuttle.ui.screens.library

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import kotlinx.collections.immutable.ImmutableList

/** Everything [LibraryOverflowMenu] needs to wire up the shared "Add to playlist" row and its submenu. */
data class AddToPlaylistMenuSpec<T, R : PlaylistData>(
    val playableItem: T,
    val playlists: ImmutableList<Playlist>,
    val onAddToPlaylist: (playlist: Playlist, playlistData: PlaylistData) -> Unit,
    val playlistDataCreator: (playableItem: T) -> R,
    val onShowCreatePlaylistDialog: (playableItem: T) -> Unit,
)

/** The items a [LibraryOverflowMenu] renders inside its dropdown. */
interface LibraryMenuScope {
    /** A single dropdown row; closes the menu after [onClick] runs. */
    @Composable
    fun MenuItem(
        text: String,
        onClick: () -> Unit,
    )

    /** The shared "Add to playlist" row; closes the menu and opens the playlist submenu. Requires an [AddToPlaylistMenuSpec] on the enclosing [LibraryOverflowMenu]. */
    @Composable
    fun AddToPlaylistMenuItem()
}

private class LibraryMenuScopeImpl(
    private val closeMenu: () -> Unit,
    private val openAddToPlaylist: () -> Unit,
) : LibraryMenuScope {
    @Composable
    override fun MenuItem(
        text: String,
        onClick: () -> Unit,
    ) {
        DropdownMenuItem(
            text = { Text(text) },
            onClick = {
                onClick()
                closeMenu()
            },
        )
    }

    @Composable
    override fun AddToPlaylistMenuItem() {
        DropdownMenuItem(
            text = { Text(stringResource(id = R.string.menu_title_add_to_playlist)) },
            onClick = {
                closeMenu()
                openAddToPlaylist()
            },
            trailingIcon = {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            },
        )
    }
}

/**
 * The overflow ("more options") menu shared by every library list row: a `MoreVert` icon button
 * that opens a dropdown built from [items].
 */
@Composable
fun LibraryOverflowMenu(
    contentDescription: String,
    modifier: Modifier = Modifier,
    items: @Composable LibraryMenuScope.() -> Unit,
) {
    var isMenuOpened by remember { mutableStateOf(false) }
    val scope = remember {
        LibraryMenuScopeImpl(
            closeMenu = { isMenuOpened = false },
            openAddToPlaylist = {},
        )
    }

    IconButton(
        modifier = modifier,
        onClick = { isMenuOpened = true },
    ) {
        Icon(
            modifier = Modifier.size(16.dp),
            imageVector = Icons.Default.MoreVert,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onBackground,
        )
        DropdownMenu(
            expanded = isMenuOpened,
            onDismissRequest = { isMenuOpened = false },
        ) {
            scope.items()
        }
    }
}

/**
 * The overflow ("more options") menu shared by every library list row that also offers "Add to
 * playlist": a `MoreVert` icon button that opens a dropdown built from [items], plus the "Add to
 * playlist" row and its submenu, wired from [addToPlaylist].
 */
@Composable
fun <T, R : PlaylistData> LibraryOverflowMenu(
    contentDescription: String,
    addToPlaylist: AddToPlaylistMenuSpec<T, R>,
    modifier: Modifier = Modifier,
    items: @Composable LibraryMenuScope.() -> Unit,
) {
    var isMenuOpened by remember { mutableStateOf(false) }
    var isAddToPlaylistSubmenuOpen by remember { mutableStateOf(false) }
    val scope = remember {
        LibraryMenuScopeImpl(
            closeMenu = { isMenuOpened = false },
            openAddToPlaylist = { isAddToPlaylistSubmenuOpen = true },
        )
    }

    IconButton(
        modifier = modifier,
        onClick = { isMenuOpened = true },
    ) {
        Icon(
            modifier = Modifier.size(16.dp),
            imageVector = Icons.Default.MoreVert,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onBackground,
        )
        DropdownMenu(
            expanded = isMenuOpened,
            onDismissRequest = { isMenuOpened = false },
        ) {
            scope.items()
        }
        AddToPlaylistSubmenu(
            playableItem = addToPlaylist.playableItem,
            expanded = isAddToPlaylistSubmenuOpen,
            onDismiss = { isAddToPlaylistSubmenuOpen = false },
            playlists = addToPlaylist.playlists,
            onAddToPlaylist = addToPlaylist.onAddToPlaylist,
            playlistDataCreator = addToPlaylist.playlistDataCreator,
            onShowCreatePlaylistDialog = addToPlaylist.onShowCreatePlaylistDialog,
        )
    }
}
