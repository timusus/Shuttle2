package com.simplecityapps.shuttle.ui.screens.tageditor

import com.simplecityapps.mediaprovider.model.AudioFile
import com.simplecityapps.shuttle.model.Song
import javax.inject.Inject

/** A song the editor can write, with the tags read from its file. */
data class EditableSong(
    val song: Song,
    val file: AudioFile,
)

data class SongTags(
    val editable: List<EditableSong>,
    /** Songs whose file couldn't be read or can't be written; the editor lists them and leaves them alone. */
    val skipped: List<Song>,
)

/** Reads the tags of the songs about to be edited, from their files rather than the library. */
class ReadSongTags @Inject constructor(
    private val tagFileAccess: TagFileAccess,
) {
    suspend operator fun invoke(
        songs: List<Song>,
        onProgress: (read: Int, total: Int) -> Unit = { _, _ -> },
    ): SongTags {
        val editable = mutableListOf<EditableSong>()
        val skipped = mutableListOf<Song>()
        songs.forEachIndexed { index, song ->
            onProgress(index, songs.size)
            val file = tagFileAccess.read(song)
            if (file != null) editable += EditableSong(song, file) else skipped += song
        }
        onProgress(songs.size, songs.size)
        return SongTags(editable, skipped)
    }
}
