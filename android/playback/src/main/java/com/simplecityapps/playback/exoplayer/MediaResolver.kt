package com.simplecityapps.playback.exoplayer

import com.simplecityapps.mediaprovider.MediaInfoProvider
import com.simplecityapps.shuttle.model.Song

/** Where to play a song from: its [uri] and [mimeType], and whether it streams over the network (which picks the wake mode). */
data class ResolvedMedia(
    val uri: String,
    val mimeType: String,
    val isRemote: Boolean
)

/** Resolves a [Song] to the media [ExoPlayerPlayback] queues for it. */
fun interface MediaResolver {
    suspend fun resolve(song: Song): ResolvedMedia
}

class MediaInfoMediaResolver(private val mediaInfoProvider: MediaInfoProvider) : MediaResolver {
    override suspend fun resolve(song: Song): ResolvedMedia {
        val mediaInfo = mediaInfoProvider.getMediaInfo(song)
        return ResolvedMedia(
            uri = mediaInfo.path.toString(),
            mimeType = mediaInfo.mimeType,
            isRemote = mediaInfo.isRemote
        )
    }
}
