package com.simplecityapps.shuttle.ui.actions

import android.content.ClipData
import android.content.Intent
import androidx.core.net.toUri
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

/** A share sheet's content, kept free of Android types so it can travel in a [MediaActionResult]. */
data class ShareRequest(
    val text: String,
    /** `content://` URIs of the song files to attach. */
    val streams: List<String>,
    val mimeType: String,
) {
    /** An [Intent.ACTION_SEND] (or `SEND_MULTIPLE`) intent, to wrap in [Intent.createChooser]. */
    fun toIntent(): Intent {
        val uris = streams.map { it.toUri() }
        val intent = if (uris.size > 1) {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        } else {
            Intent(Intent.ACTION_SEND).apply { uris.firstOrNull()?.let { putExtra(Intent.EXTRA_STREAM, it) } }
        }
        return intent.apply {
            type = mimeType
            putExtra(Intent.EXTRA_TEXT, text)
            if (uris.isNotEmpty()) {
                clipData = ClipData.newRawUri(null, uris.first()).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }
}
