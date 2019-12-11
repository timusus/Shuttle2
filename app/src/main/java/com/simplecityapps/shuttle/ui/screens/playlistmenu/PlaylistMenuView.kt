package com.simplecityapps.shuttle.ui.screens.playlistmenu

import android.content.Context
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.fragment.app.FragmentManager
import com.simplecityapps.mediaprovider.model.Playlist
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.error.userDescription
import timber.log.Timber

private const val playlistGroupId = 100
private const val playlistCreateId = 999999

class PlaylistMenuView(
    private val context: Context,
    private val presenter: PlaylistMenuContract.Presenter,
    private val fragmentManager: FragmentManager
) : PlaylistMenuContract.View {

    override fun onPlaylistCreated(playlist: Playlist) {
        showPlaylistCreatedToast(context, playlist)
    }

    override fun onAddedToPlaylist(playlist: Playlist, playlistData: PlaylistData) {
        showAddedToPlaylistToast(context, playlist, playlistData)
    }

    override fun onPlaylistAddFailed(error: Error) {
        showPlaylistAddFailedToast(context, error)
    }

    override fun showCreatePlaylistDialog(playlistData: PlaylistData) {
        CreatePlaylistDialogFragment.newInstance(playlistData).show(fragmentManager)
    }

    override fun onSave(text: String, playlistData: PlaylistData) {
        presenter.createPlaylist(text, playlistData)
    }

    fun handleMenuItem(
        item: MenuItem,
        playlistData: PlaylistData
    ): Boolean {
        getSelectedPlaylist(item)?.let { playlist ->
            presenter.addToPlaylist(playlist, playlistData)
            return true
        }
        if (isCreatePlaylistMenuItem(item)) {
            CreatePlaylistDialogFragment.newInstance(playlistData).show(fragmentManager)
            return true
        }

        return false
    }


    /**
     * Clears the submenu found at R.id.playlist, and populates it with items representing the passed in playlists.
     *
     * The menu items are given a group id of [playlistGroupId], and assigned id's according to their index in [playlists]
     *
     * The 'create playlist' menu item is assigned  an id of [playlistCreateId]
     */
    fun createPlaylistMenu(menu: Menu) {
        val subMenu = menu.findItem(R.id.playlist)?.subMenu
        subMenu?.let {
            subMenu.clear()
            subMenu.add(Menu.NONE, playlistCreateId, 0, "+ New Playlist")
            for ((index, playlist) in presenter.playlists.withIndex()) {
                subMenu.add(playlistGroupId, index, index, playlist.name)
            }
        } ?: Timber.e("Failed to create playlist menu. 'R.id.playlist' not found in menu")
    }

    /**
     * Returns the [Playlist] associated with the passed in [MenuItem], or null if none is found.
     *
     * The menu item must have a group id of [playlistGroupId], and menu item id's should correspond to the index of the playlist in [playlists].
     *
     * Typically used in association with [createPlaylistMenu]]
     */
    private fun getSelectedPlaylist(item: MenuItem): Playlist? {
        if (item.groupId == playlistGroupId) {
            return presenter.playlists.getOrNull(item.itemId)
        }
        return null
    }

    /**
     * Returns true if the [item] has an id of [playlistCreateId]
     *
     * Typically used in association with [createPlaylistMenu]
     */
    private fun isCreatePlaylistMenuItem(item: MenuItem): Boolean {
        return item.itemId == playlistCreateId
    }

    private fun showPlaylistCreatedToast(context: Context, playlist: Playlist) {
        Toast.makeText(context, "'${playlist.name}' successfully created", Toast.LENGTH_LONG).show()
    }

    private fun showAddedToPlaylistToast(context: Context, playlist: Playlist, playlistData: PlaylistData) {
        Toast.makeText(context, playlistData.getPlaylistSavedMessage(playlist.name), Toast.LENGTH_LONG).show()
    }

    private fun showPlaylistAddFailedToast(context: Context, error: Error) {
        Toast.makeText(context, "Failed to add songs to playlist: ${error.userDescription()}", Toast.LENGTH_LONG).show()
    }
}