package com.simplecityapps.playback.mediasession

import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song

/**
 * What could be learned about an audio file another app asked us to play (an ACTION_VIEW intent, or a media
 * controller's request to play a URI).
 *
 * @param uri the URI as given.
 * @param filePath the file's absolute path, when the URI is a file:// URI or its provider reports one.
 * @param documentId the Storage Access Framework document id, when the URI is a document URI.
 * @param authority the URI's content provider authority, when it's a content:// URI.
 */
data class OpenedAudio(
    val uri: String,
    val filePath: String? = null,
    val documentId: String? = null,
    val authority: String? = null,
    val displayName: String? = null,
    val mimeType: String? = null,
    val size: Long? = null,
    val title: String? = null,
    val artist: String? = null,
    val albumArtist: String? = null,
    val album: String? = null,
    val durationMs: Int? = null
) {
    /**
     * The library song for this file, or null when it isn't in the library. A library song is preferred
     * over [toTransientSong], since it plays from its own path (which doesn't depend on the caller's URI
     * grant) and its plays are counted.
     *
     * Matches on the song's path: the URI itself, the file path (MediaStore songs carry file paths), or the
     * SAF document id (songs found through a picked folder carry tree document URIs, which name the same
     * document id under the same authority as a plain document URI for that file).
     *
     * The same file can be in the library more than once, a row per provider (#420), so every match is a
     * candidate and the choice doesn't depend on the library's order (RS-57): a song that isn't excluded first,
     * then S2's own scan over MediaStore's (the provider order of [MediaProviderType]), then the lowest id.
     */
    fun findIn(songs: List<Song>): Song? = songs.filter(::isSameFile).minWithOrNull(libraryRowPreference)

    private fun isSameFile(song: Song): Boolean {
        if (song.path == uri || (filePath != null && song.path == filePath)) return true
        if (documentId == null) return false
        val (songAuthority, songDocumentId) = documentUriParts(song.path) ?: return false
        return songAuthority == authority && songDocumentId == documentId
    }

    /**
     * A song that plays straight from [uri], for a file that isn't in the library. Its id is negative, so it
     * never matches a library song: plays aren't counted, and it isn't restored with the queue after the app
     * is killed (by which time a temporary URI grant would have lapsed anyway).
     */
    fun toTransientSong(): Song = Song(
        id = transientId(uri),
        name = title?.ifBlank { null } ?: displayName?.substringBeforeLast('.')?.ifBlank { null } ?: uri.substringAfterLast('/'),
        albumArtist = albumArtist?.ifBlank { null },
        artists = listOfNotNull(artist?.ifBlank { null }),
        album = album?.ifBlank { null },
        track = null,
        disc = null,
        duration = durationMs ?: 0,
        date = null,
        genres = emptyList(),
        path = uri,
        size = size ?: 0,
        mimeType = mimeType ?: "audio/*",
        lastModified = null,
        lastPlayed = null,
        lastCompleted = null,
        playCount = 0,
        playbackPosition = 0,
        blacklisted = false,
        // Not tag-editable, and not remote, so it plays without a network wake lock.
        mediaProvider = MediaProviderType.MediaStore,
        lyrics = null,
        grouping = null,
        bitRate = null,
        bitDepth = null,
        sampleRate = null,
        channelCount = null
    )

    companion object {
        /** Which of several library rows for the same file plays it (see [findIn]). */
        private val libraryRowPreference: Comparator<Song> =
            compareBy<Song>({ it.blacklisted }, { it.mediaProvider.ordinal }, { it.id })

        /** A negative id derived from [uri], so the same file keeps the same id and never collides with a library row. */
        fun transientId(uri: String): Long = -(uri.hashCode().toLong() and 0xFFFFFFFFL) - 1

        /**
         * The authority and decoded document id of a content:// document URI (`content://<authority>/document/<id>`
         * or `content://<authority>/tree/<tree id>/document/<id>`), or null for anything else.
         */
        fun documentUriParts(uri: String): Pair<String, String>? {
            if (!uri.startsWith("content://")) return null
            val authority = uri.removePrefix("content://").substringBefore('/')
            val segments = uri.removePrefix("content://").split('/').drop(1)
            val documentIndex = segments.indexOf("document")
            if (documentIndex == -1 || documentIndex + 1 >= segments.size) return null
            return authority to percentDecode(segments[documentIndex + 1])
        }

        private fun percentDecode(value: String): String {
            val bytes = java.io.ByteArrayOutputStream()
            var i = 0
            while (i < value.length) {
                val c = value[i]
                if (c == '%' && i + 2 < value.length) {
                    val byte = value.substring(i + 1, i + 3).toIntOrNull(16)
                    if (byte != null) {
                        bytes.write(byte)
                        i += 3
                        continue
                    }
                }
                bytes.write(c.toString().toByteArray(Charsets.UTF_8))
                i++
            }
            return bytes.toString(Charsets.UTF_8.name())
        }
    }
}
