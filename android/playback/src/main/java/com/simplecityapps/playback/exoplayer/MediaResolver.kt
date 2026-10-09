package com.simplecityapps.playback.exoplayer

import com.simplecityapps.mediaprovider.MediaInfoProvider
import com.simplecityapps.mediaprovider.TimeSeekableStream
import com.simplecityapps.shuttle.model.Song

/**
 * Where to play a song from: its [uri] and [mimeType], whether it streams over the network (which picks the wake mode),
 * for a transcode that seeks by time rather than byte range, how ([timeSeek]), and whether the server drops it when another
 * transcode starts ([isReplaceableTranscode]: see [com.simplecityapps.mediaprovider.MediaInfo]).
 */
data class ResolvedMedia(
    val uri: String,
    val mimeType: String,
    val isRemote: Boolean,
    val timeSeek: TimeSeekableStream? = null,
    val isReplaceableTranscode: Boolean = false
)

/** Resolves a [Song] to the media the player streams for it, for the play [playId]. */
fun interface MediaResolver {
    suspend fun resolve(
        song: Song,
        playId: String?
    ): ResolvedMedia
}

class MediaInfoMediaResolver(private val mediaInfoProvider: MediaInfoProvider) : MediaResolver {
    override suspend fun resolve(
        song: Song,
        playId: String?
    ): ResolvedMedia {
        val mediaInfo = mediaInfoProvider.getMediaInfo(song, playId = playId)
        return ResolvedMedia(
            uri = mediaInfo.path.toString(),
            mimeType = mediaInfo.mimeType,
            isRemote = mediaInfo.isRemote,
            timeSeek = mediaInfo.timeSeek,
            isReplaceableTranscode = mediaInfo.isReplaceableTranscode
        )
    }
}
