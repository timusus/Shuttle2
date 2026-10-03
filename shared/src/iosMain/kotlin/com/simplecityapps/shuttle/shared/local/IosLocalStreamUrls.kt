package com.simplecityapps.shuttle.shared.local

import com.simplecityapps.mediaprovider.StreamUrlProvider
import com.simplecityapps.shuttle.model.Song
import dev.zacsweers.metro.Inject

/** A local song's file, where [IosLocalFiles] finds it now: never gated, as a server's stream may be. */
@Inject
class IosLocalStreamUrls(
    private val localFiles: IosLocalFiles
) : StreamUrlProvider {
    override fun handles(scheme: String?): Boolean = scheme == IosLocalFiles.SCHEME

    override fun streamUrl(
        song: Song,
        startPositionMs: Long
    ): String = localFiles.fileUrl(song.path) ?: throw IllegalStateException("${song.path} is out of reach")
}
