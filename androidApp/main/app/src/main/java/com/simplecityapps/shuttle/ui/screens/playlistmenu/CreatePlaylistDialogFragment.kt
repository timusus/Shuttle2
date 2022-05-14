package com.simplecityapps.shuttle.ui.screens.playlistmenu

import android.os.Bundle
import com.simplecityapps.shuttle.ui.common.dialog.EditTextAlertDialog
import com.simplecityapps.shuttle.ui.common.utils.withArgs

class CreatePlaylistDialogFragment : EditTextAlertDialog() {

    interface Listener {
        fun onSave(text: String, playlistData: PlaylistData)
    }

    private lateinit var playlistData: PlaylistData

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        playlistData = arguments?.getParcelable(ARG_DATA)!!
    }

    override fun onSave(string: String) {
        (parentFragment as? Listener)?.onSave(string, playlistData)
    }

    override fun isValid(string: String?): Boolean {
        return string?.isNotEmpty() ?: false
    }

    companion object {

        const val ARG_DATA = "data"

        fun newInstance(playlistData: PlaylistData, hint: String?) = CreatePlaylistDialogFragment().withArgs {
            putParcelable(ARG_DATA, playlistData)
            putString(ARG_HINT, hint)
        }
    }
}
