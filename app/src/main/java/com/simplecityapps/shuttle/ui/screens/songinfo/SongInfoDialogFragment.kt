package com.simplecityapps.shuttle.ui.screens.songinfo

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.marginEnd
import androidx.core.view.marginStart
import androidx.core.view.setPadding
import androidx.fragment.app.DialogFragment
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.shuttle.dagger.Injectable
import com.simplecityapps.shuttle.ui.common.utils.dp
import com.simplecityapps.shuttle.ui.common.utils.toHms
import com.simplecityapps.shuttle.ui.common.view.setMargins

class SongInfoDialogFragment : DialogFragment(), Injectable {

    private lateinit var song: Song

    override fun onAttach(context: Context) {
        super.onAttach(context)

        song = SongInfoDialogFragmentArgs.fromBundle(arguments!!).song
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        val lhs = LinearLayout(context!!, null).apply {
            orientation = LinearLayout.VERTICAL
        }
        val rhs = LinearLayout(context!!, null).apply {
            orientation = LinearLayout.VERTICAL
        }

        val rootView = LinearLayout(context!!, null).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dp)
        }

        val map = mapOf(
            "Title" to song.name,
            "Track #" to song.track.toString(),
            "Duration" to song.duration.toHms("Unknown"),
            "Artist" to song.albumArtistName,
            "Album" to song.albumName,
            "Year" to song.year.toString(),
            "Disc" to song.disc.toString(),
            "Mime Type" to song.mimeType,
            "Size" to "${"%.2f".format((song.size / 1024f / 1024f))}MB"
        )

        for ((key, value) in map) {
            rootView.addView(LinearLayout(context!!, null).apply {

                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                setMargins(marginStart, 4.dp, marginEnd, 4.dp)

                addView(TextView(context!!).apply {
                    text = key
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                })
                addView(Space(context!!, null), 8.dp, LinearLayout.LayoutParams.MATCH_PARENT)
                addView(TextView(context!!).apply {
                    text = value
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                })
            })
        }

        return AlertDialog.Builder(context!!)
            .setTitle("Song Info")
            .setView(rootView)
            .setNegativeButton("Close", null)
            .show()
    }
}