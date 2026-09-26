package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.shuttle.model.Song
import javax.inject.Inject

/**
 * What to share for a selection: each song as "Title – Artist" text, plus the files of the local songs that have a
 * `content://` path. Remote songs are shared as text only; their stream URLs carry the server's auth token.
 */
class ShareSongs @Inject constructor(
    private val resolveSongs: ResolveSongs,
) {
    /** Null when the selection has no songs. */
    suspend operator fun invoke(selection: MediaSelection): ShareRequest? {
        val songs = resolveSongs(selection)
        if (songs.isEmpty()) return null
        val files = songs.filter { !it.mediaProvider.remote && it.path.startsWith("content://") }
        return ShareRequest(
            text = songs.joinToString("\n") { it.shareText() },
            streams = files.map { it.path },
            mimeType = when {
                files.isEmpty() -> "text/plain"
                files.map { it.mimeType }.distinct().size == 1 -> files.first().mimeType
                else -> "audio/*"
            },
        )
    }

    private fun Song.shareText(): String = listOfNotNull(name, friendlyArtistName).joinToString(" – ")
}

/** A share sheet's content, kept free of Android types; the app turns it into an `Intent` with `toIntent()`. */
data class ShareRequest(
    val text: String,
    /** `content://` URIs of the song files to attach. */
    val streams: List<String>,
    val mimeType: String,
)
