package com.simplecityapps.shuttle.ui.screens.playlistmenu

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.FragmentManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.simplecityapps.mediaprovider.model.Playlist
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.error.userDescription
import com.squareup.phrase.Phrase
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
        CreatePlaylistDialogFragment.newInstance(playlistData, context.getString(R.string.playlist_create_dialog_playlist_name_hint)).show(fragmentManager)
    }

    override fun onSave(text: String, playlistData: PlaylistData) {
        presenter.createPlaylist(text, playlistData)
    }

    @SuppressLint("InflateParams")
    override fun onAddToPlaylistWithDuplicates(playlist: Playlist, playlistData: PlaylistData, deduplicatedPlaylistData: PlaylistData.Songs, duplicates: List<Song>) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_playlist_duplicate, null)
        val subtitle: TextView = view.findViewById(R.id.title)
        val alwaysAddSwitch: SwitchCompat = view.findViewById(R.id.alwaysAddSwitch)

        subtitle.text = Phrase.fromPlural(context, R.plurals.playlist_menu_duplicates_dialog_subtitle, duplicates.size)
            .putOptional("count", duplicates.size)
            .put("playlist_name", playlist.name)
            .format()

        alwaysAddSwitch.setOnCheckedChangeListener { _, isChecked ->
            presenter.setIgnorePlaylistDuplicates(isChecked)
        }

        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.playlist_menu_duplicates_dialog_title))
            .setView(view)
            .setNegativeButton(context.getString(R.string.playlist_menu_duplicates_dialog_button_skip)) { _, _ ->
                presenter.addToPlaylist(playlist, deduplicatedPlaylistData, true)
            }
            .setPositiveButton(context.getString(R.string.playlist_menu_duplicates_dialog_button_add)) { _, _ ->
                presenter.addToPlaylist(playlist, playlistData, true)
            }
            .show()
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
            CreatePlaylistDialogFragment.newInstance(playlistData, context.getString(R.string.playlist_create_dialog_playlist_name_hint)).show(fragmentManager)
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
        Timber.i("createPlaylistMenu")
        val subMenu = menu.findItem(R.id.playlist)?.subMenu
        subMenu?.let {
            subMenu.clear()
            subMenu.add(Menu.NONE, playlistCreateId, 0, context.getString(R.string.playlist_menu_create_playlist))
            for ((index, playlist) in presenter.playlists.withIndex()) {
                Timber.i("adding playlist $index")

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
        Toast.makeText(
            context,
            Phrase.from(context, R.string.playlist_menu_create_playlist_success)
                .put("playlist_name", playlist.name)
                .format(),
            Toast.LENGTH_LONG
        ).show()
    }

    private fun showAddedToPlaylistToast(context: Context, playlist: Playlist, playlistData: PlaylistData) {
        Toast.makeText(context, playlistData.getPlaylistSavedMessage(context.resources, playlist.name), Toast.LENGTH_LONG).show()
    }

    private fun showPlaylistAddFailedToast(context: Context, error: Error) {
        Toast.makeText(
            context,
            Phrase.from(context, R.string.playlist_menu_create_playlist_failure)
                .put("error_message", error.userDescription(context.resources))
                .format(),
            Toast.LENGTH_LONG
        ).show()
    }
}