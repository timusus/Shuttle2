package com.simplecityapps.shuttle.ui.screens.songinfo

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.view.marginEnd
import androidx.core.view.marginStart
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.utils.dp
import com.simplecityapps.shuttle.ui.common.utils.toHms
import com.simplecityapps.shuttle.ui.common.utils.withArgs
import com.simplecityapps.shuttle.ui.common.view.setMargins
import dagger.hilt.android.AndroidEntryPoint
import java.net.URLDecoder

@AndroidEntryPoint
class SongInfoDialogFragment : BottomSheetDialogFragment() {
    private lateinit var song: com.simplecityapps.shuttle.model.Song

    override fun onAttach(context: Context) {
        super.onAttach(context)

        song = requireArguments().getParcelable<com.simplecityapps.shuttle.model.Song>(ARG_SONG) as com.simplecityapps.shuttle.model.Song
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val contextThemeWrapper = ContextThemeWrapper(activity, requireContext().theme)
        return inflater.cloneInContext(contextThemeWrapper).inflate(R.layout.fragment_dialog_song_info, container, false)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        val containerView: LinearLayout = view.findViewById(R.id.containerView)

        val map =
            mapOf(
                getString(R.string.song_info_track_title) to song.name.orEmpty(),
                getString(R.string.song_info_track_number) to song.track?.toString().orEmpty(),
                getString(R.string.song_info_duration) to song.duration.toHms(getString(R.string.song_info_unknown)),
                getString(R.string.song_info_album_artist) to song.albumArtist.orEmpty(),
                getString(R.string.song_info_artists) to song.artists.joinToString(", "),
                getString(R.string.song_info_album) to song.album.orEmpty(),
                getString(R.string.song_info_year) to song.date?.year?.toString().orEmpty(),
                getString(R.string.song_info_disc) to song.disc?.toString().orEmpty(),
                getString(R.string.song_info_play_count) to song.playCount.toString(),
                getString(R.string.song_info_genres) to song.genres.joinToString(", "),
                getString(R.string.song_info_path) to song.path.sanitise(),
                getString(R.string.song_info_mime_type) to song.mimeType,
                getString(R.string.song_info_size) to "${"%.2f".format((song.size / 1024f / 1024f))}MB",
                getString(R.string.song_info_bit_rate) to song.bitRate?.toString()?.let { "$it kb/s" },
                getString(R.string.song_info_sample_rate) to song.sampleRate?.toString()?.let { "$it kHz" },
                getString(R.string.song_info_channel_count) to song.channelCount?.toString(),
                getString(R.string.song_info_lyrics) to song.lyrics
            )

        for ((key, value) in map) {
            containerView.addView(
                LinearLayout(requireContext(), null).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    setMargins(marginStart, 4.dp, marginEnd, 4.dp)

                    addView(
                        TextView(requireContext()).apply {
                            text = key
                            gravity = Gravity.START or Gravity.TOP
                            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                        }
                    )
                    addView(Space(requireContext(), null), 8.dp, LinearLayout.LayoutParams.MATCH_PARENT)
                    addView(
                        TextView(requireContext()).apply {
                            text = value
                            gravity = Gravity.START or Gravity.CENTER_VERTICAL
                            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.5f)
                        }
                    )
                }
            )
        }
    }

    fun show(fragmentManager: FragmentManager) {
        show(fragmentManager, TAG)
    }

    fun String.sanitise(): String {
        if (DocumentsContract.isDocumentUri(requireContext(), Uri.parse(this))) {
            return try {
                URLDecoder.decode(song.path, Charsets.UTF_8.name()).substringAfterLast(':')
            } catch (e: Exception) {
                song.path
            }
        }
        return this
    }

    companion object {
        private const val ARG_SONG = "song"

        private const val TAG = "SongInfoDialogFragment"

        fun newInstance(song: com.simplecityapps.shuttle.model.Song): SongInfoDialogFragment =
            SongInfoDialogFragment().withArgs {
                putParcelable(ARG_SONG, song)
            }
    }
}
