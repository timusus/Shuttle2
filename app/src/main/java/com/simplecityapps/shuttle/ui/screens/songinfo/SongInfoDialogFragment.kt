package com.simplecityapps.shuttle.ui.screens.songinfo

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import androidx.core.view.marginEnd
import androidx.core.view.marginStart
import androidx.core.view.setPadding
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.shuttle.dagger.Injectable
import com.simplecityapps.shuttle.ui.common.utils.dp
import com.simplecityapps.shuttle.ui.common.utils.toHms
import com.simplecityapps.shuttle.ui.common.utils.withArgs
import com.simplecityapps.shuttle.ui.common.view.setMargins
import java.net.URLDecoder

class SongInfoDialogFragment : DialogFragment(), Injectable {

    private lateinit var song: Song

    override fun onAttach(context: Context) {
        super.onAttach(context)

        song = requireArguments().getParcelable<Song>(ARG_SONG) as Song
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        val scrollView = ScrollView(requireContext(), null)
        val linearLayout = LinearLayout(requireContext(), null).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dp)
        }

        val map = mapOf(
            "Title" to song.name.orEmpty(),
            "Track #" to song.track?.toString().orEmpty(),
            "Duration" to song.duration.toHms("Unknown"),
            "Album Artist" to song.albumArtist.orEmpty(),
            "Artist(s)" to song.artists.joinToString(", "),
            "Album" to song.album.orEmpty(),
            "Year" to song.year?.toString().orEmpty(),
            "Disc" to song.disc?.toString().orEmpty(),
            "Play count" to song.playCount.toString(),
            "Genre(s)" to song.genres.joinToString(", "),
            "Path" to URLDecoder.decode(song.path),
            "Mime Type" to song.mimeType,
            "Size" to "${"%.2f".format((song.size / 1024f / 1024f))}MB",
            "Lyrics" to song.lyrics,
        )

        for ((key, value) in map) {
            linearLayout.addView(LinearLayout(requireContext(), null).apply {

                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                setMargins(marginStart, 4.dp, marginEnd, 4.dp)

                addView(TextView(requireContext()).apply {
                    text = key
                    gravity = Gravity.START or Gravity.TOP
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                })
                addView(Space(requireContext(), null), 8.dp, LinearLayout.LayoutParams.MATCH_PARENT)
                addView(TextView(requireContext()).apply {
                    text = value
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.5f)
                })
            })
        }

        scrollView.addView(linearLayout)

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle("Song Info")
            .setView(scrollView)
            .setNegativeButton("Close", null)
            .show()
    }

    fun show(fragmentManager: FragmentManager) {
        show(fragmentManager, TAG)
    }

    companion object {

        private const val ARG_SONG = "song"

        private const val TAG = "SongInfoDialogFragment"

        fun newInstance(song: Song): SongInfoDialogFragment = SongInfoDialogFragment().withArgs {
            putParcelable(ARG_SONG, song)
        }
    }
}