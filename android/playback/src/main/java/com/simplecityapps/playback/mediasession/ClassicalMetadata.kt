package com.simplecityapps.playback.mediasession

import androidx.media3.common.MediaMetadata
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.model.lockScreenText
import com.simplecityapps.shuttle.model.workProgress

/**
 * This metadata with the movement as the title and "Composer: Work · lead performer" as the artist, for the song at
 * [index] of [queue] when it sits in a work block. Anything else comes back unchanged: [lockScreenText] alone would
 * also reword a lone classical song, which Classical display leaves as it is.
 */
internal fun MediaMetadata.withClassicalText(
    queue: List<Song>,
    index: Int
): MediaMetadata {
    if (workProgress(queue, index) == null) return this
    val text = lockScreenText(queue, index) ?: return this
    return buildUpon()
        .setTitle(text.title)
        .setArtist(text.artist ?: artist)
        .setComposer(queue[index].composer)
        .build()
}
