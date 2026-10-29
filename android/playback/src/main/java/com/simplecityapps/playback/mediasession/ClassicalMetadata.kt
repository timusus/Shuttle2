package com.simplecityapps.playback.mediasession

import androidx.media3.common.MediaMetadata
import com.simplecityapps.playback.queue.QueueItem
import com.simplecityapps.shuttle.model.classicalSurfaceText
import com.simplecityapps.shuttle.model.songsView

/** The movement as title and "Composer: Work · lead performer" as artist, for one song of a work. */
internal class ClassicalText(val title: String, val artist: String?, val composer: String?)

/**
 * The text for the song at [index] of [queue] when it sits in a work block, else null: [lockScreenText] alone would
 * also reword a lone classical song, which Classical display leaves as it is.
 */
internal fun classicalText(
    queue: List<QueueItem>,
    index: Int
): ClassicalText? {
    val text = classicalSurfaceText(queue.songsView { it.song }, index) ?: return null
    return ClassicalText(text.title, text.artist, queue[index].song.composer)
}

internal fun MediaMetadata.withClassicalText(text: ClassicalText): MediaMetadata = buildUpon()
    .setTitle(text.title)
    .setArtist(text.artist ?: artist)
    .setComposer(text.composer)
    .build()
